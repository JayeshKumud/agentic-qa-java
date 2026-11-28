package com.agenticqa.core;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.PageComponents;
import com.agenticqa.ui.healing.ElementLookup;
import com.agenticqa.ui.healing.HealEvent;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything one scenario needs while it runs: the browser page, the self-healing engine, and the data its steps
 * share ({@link #data()}, GUI and API steps alike). Trace and video files are handled by {@link ScenarioArtifacts}. Cucumber's
 * PicoContainer creates one instance per scenario and injects it into every step class, so parallel scenarios
 * never share a browser or data.
 * <p>
 * The browser context is created lazily, the first time a step asks for the page, in the browser of the current
 * worker thread ({@link BrowserPool}), so API-only scenarios never start a browser. Closing the session closes the
 * context, not the browser. API calls do not go through the session: API classes ({@code ApiFactory}) call their
 * services directly with REST Assured.
 * <p>
 * Thread safety: a scenario runs on one thread from its first hook to its last, and nothing in a session is
 * shared with other scenarios, so parallel scenarios are isolated by construction.
 */
public class TestSession {

    /**
     * The session of the scenario running on each thread. It points to the session PicoContainer created (TestInitializeHooks
     * binds it before every scenario, {@link #close} removes it), so static code and injected code see the same
     * session. It is the only ThreadLocal of the session: per-scenario data lives in the session's own fields and
     * store, so nothing else has to be cleared.
     */
    private static final ThreadLocal<TestSession> CURRENT = new ThreadLocal<>();

    /**
     * Files the session kept when it closed.
     *
     * @param trace the Playwright trace, or null if none was kept
     * @param video the video of the page, or null if none was kept
     */
    public record SavedFiles(Path trace, Path video) {}

    private final TestData data = new TestData();
    private final Map<Class<? extends PageComponents>, PageComponents> pageComponents = new HashMap<>();
    /** Per window: the column that picks its grid ({@link #useGrid}); no entry = the window's only grid. */
    private final Map<Page, String> gridColumns = new IdentityHashMap<>();
    /** Per window: the locator name of the container that picks its grid ({@link #useGridIn}). */
    private final Map<Page, String> gridContainers = new IdentityHashMap<>();

    private BrowserContext context;
    private Page page;
    private Healer healer;
    private String scenarioName = "scenario";

    /**
     * Remembers the scenario name; it is used to name the trace file when the scenario fails.
     *
     * @param name the Cucumber scenario name
     */
    public void scenarioName(String name) {
        this.scenarioName = name;
    }

    /**
     * The data this scenario's steps share, under typed keys: {@code session.data().put(Keys.CUSTOMER, customer)}.
     *
     * @return this scenario's test data
     */
    public TestData data() {
        return data;
    }

    /**
     * The scenario's browser page, created on first use.
     *
     * @return this scenario's page
     */
    public synchronized Page page() {
        if (page == null) page = context().newPage();
        return page;
    }

    /**
     * Tells whether a page was opened, so hooks never create one just to take a screenshot.
     *
     * @return true if page() was called before
     */
    public synchronized boolean hasPage() {
        return page != null;
    }

    /**
     * The self-healing engine of this scenario, created on first use.
     *
     * @return this scenario's healer
     */
    public synchronized Healer healer() {
        if (healer == null) healer = new Healer();
        return healer;
    }

    /**
     * Locators healed in this scenario, without creating a healer just to ask.
     *
     * @return the heals, empty if none
     */
    public synchronized List<HealEvent> healEvents() {
        return healer == null ? List.of() : healer.events();
    }

    /**
     * The last element lookups of this scenario, oldest first (see {@link Healer#lookups()}).
     *
     * @return an unmodifiable list, empty if no page object looked up an element
     */
    public synchronized List<ElementLookup> elementLookups() {
        return healer == null ? List.of() : healer.lookups();
    }

    /**
     * Makes this the current session of this thread, so {@link #current()} finds it. TestInitializeHooks calls it before every
     * scenario; {@link #close} undoes it.
     */
    public void bindToCurrentThread() {
        CURRENT.set(this);
    }

    /**
     * The session of the scenario running on this thread, for code that PicoContainer does not create (static
     * helpers, listeners, page factories). Injected classes can keep receiving the session in their constructor;
     * both are the same object.
     *
     * @return the current session
     * @throws IllegalStateException if no scenario is running on this thread
     */
    public static TestSession current() {
        TestSession session = CURRENT.get();
        if (session == null) {
            throw new IllegalStateException("No scenario is running on this thread. TestSession.current() works"
                    + " inside a scenario, after TestInitializeHooks.bindSession has bound the session, and only on the scenario's thread.");
        }
        return session;
    }

    /**
     * The browser page of the scenario running on this thread, opened on first use.
     *
     * @return the current scenario's page
     * @throws IllegalStateException if no scenario is running on this thread
     */
    public static Page currentPage() {
        return current().page();
    }

    /**
     * The scenario's page object of a type, created with {@code create} on first use and reused after that.
     * Used by {@code PageComponentsFactory}; the pages are dropped with the session.
     *
     * @param type   the page class, for example HomePage.class
     * @param create creates the page when this scenario has none of that type yet
     * @param <T>    the page type
     * @return the scenario's instance of that page
     */
    public synchronized <T extends PageComponents> T pageComponent(Class<T> type, Supplier<T> create) {
        PageComponents existing = pageComponents.get(type);
        if (existing == null) {
            existing = create.get();
            pageComponents.put(type, existing);
        }
        return type.cast(existing);
    }

    /**
     * Stores a page object that was created elsewhere, for example the page of a window the app opened, so
     * {@code pages.get(type)} returns it from now on (replacing an earlier one of that type).
     *
     * @param type the page class, for example PricePage.class
     * @param page the page object
     * @param <T>  the page type
     * @return the page object
     */
    public synchronized <T extends PageComponents> T putPageComponent(Class<T> type, T page) {
        pageComponents.put(type, page);
        return page;
    }

    /**
     * Makes {@code grid()} of every page object on this window the grid with this column, for windows with several
     * grids (the step "I use the grid with column ..."). The choice belongs to the window: other windows keep
     * theirs, and it ends with the scenario.
     *
     * It replaces a container chosen with {@link #useGridIn}.
     *
     * @param window the window
     * @param column the header text of a column only that grid has; null = the window's only grid again
     */
    public synchronized void useGrid(Page window, String column) {
        gridContainers.remove(window);
        if (column == null) gridColumns.remove(window);
        else gridColumns.put(window, column);
    }

    /**
     * Makes {@code grid()} of every page object on this window the grid in a container, for windows whose grids no
     * column tells apart (the step "I use the grid in ..."). The container is a locator name in the locator file of
     * the page object asking. It replaces a column chosen with {@link #useGrid}; the choice belongs to the window,
     * like a column.
     *
     * @param window        the window
     * @param containerName the locator name of the element the grid is in, for example "bookingsGrid"; null = the
     *                      window's only grid again
     */
    public synchronized void useGridIn(Page window, String containerName) {
        gridColumns.remove(window);
        if (containerName == null) gridContainers.remove(window);
        else gridContainers.put(window, containerName);
    }

    /**
     * @param window the window
     * @return the container locator name chosen with {@link #useGridIn} for that window, or null
     */
    public synchronized String gridContainer(Page window) {
        return gridContainers.get(window);
    }

    /**
     * The grid container chosen for a window in the scenario running on this thread, for
     * {@code PageComponents.grid()}.
     *
     * @param window the window
     * @return the container's locator name, or null if none was chosen or no scenario is running on this thread
     */
    public static String chosenGridContainer(Page window) {
        TestSession session = CURRENT.get();
        return session == null ? null : session.gridContainer(window);
    }

    /**
     * @param window the window
     * @return the column chosen with {@link #useGrid} for that window, or null for its only grid
     */
    public synchronized String gridColumn(Page window) {
        return gridColumns.get(window);
    }

    /**
     * The grid column chosen for a window in the scenario running on this thread, for {@code PageComponents.grid()}.
     *
     * @param window the window
     * @return the column, or null if none was chosen or no scenario is running on this thread
     */
    public static String chosenGridColumn(Page window) {
        TestSession session = CURRENT.get();
        return session == null ? null : session.gridColumn(window);
    }

    /**
     * The browser context, started on first use.
     *
     * @return the scenario's browser context
     */
    private synchronized BrowserContext context() {
        if (context == null) startBrowser();
        return context;
    }

    /**
     * Opens a new context in this thread's browser, with tracing switched on, and video recording unless
     * {@code ui.video} is off. All browser and page settings are applied here, once per scenario; every page and
     * page object created later inherits them.
     */
    private void startBrowser() {
        Browser browser = BrowserPool.browser();
        boolean maximized = BrowserPool.maximized();
        int width = Settings.getViewportWidth();
        int height = Settings.getViewportHeight();
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(Settings.getBaseUrl())
                .setIgnoreHTTPSErrors(Settings.isIgnoreHttpsErrors());
        if (!Settings.getPermissions().isEmpty()) {
            options.setPermissions(Settings.getPermissions()); // no Allow/Block prompt, also in new windows
        }
        if (maximized) {
            options.setViewportSize(null); // the page uses the real, maximized window size
        } else {
            options.setViewportSize(width, height);
        }
        if (Settings.recordsVideo()) {
            options.setRecordVideoDir(ScenarioArtifacts.VIDEO_RECORDING_DIR);
            if (!maximized) {
                options.setRecordVideoSize(width, height); // full size, not scaled down
            }
        }
        context = browser.newContext(options);
        context.setDefaultTimeout(Settings.getActionMs());
        context.setDefaultNavigationTimeout(Settings.getNavigationMs());
        PlaywrightAssertions.setDefaultAssertionTimeout(Settings.getAssertionMs()); // assertThat would otherwise use 5 s
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(false));
    }

    /**
     * Stops tracing (the zip is kept only when the scenario failed) and closes the context. The page's video is
     * kept when {@code ui.video} is always, or failed and the scenario failed; its raw recording is always
     * deleted ({@link ScenarioArtifacts}). The browser stays open for the next scenario on this thread.
     * Each close is attempted separately so cleanup never hides the real test result.
     *
     * @param failed true if the scenario failed
     * @return the kept trace and video; both null if nothing was kept (passed, or no browser was started)
     */
    public synchronized SavedFiles close(boolean failed) {
        ScenarioArtifacts artifacts = new ScenarioArtifacts(scenarioName);
        Path trace = context == null ? null : artifacts.stopTrace(context, failed);
        Video video = ScenarioArtifacts.videoOf(page);
        quietly(() -> { if (context != null) context.close(); }); // closing finishes writing the video
        Path keptVideo = artifacts.keepVideo(video, failed);
        context = null;
        page = null;
        pageComponents.clear();
        gridColumns.clear();
        gridContainers.clear();
        if (CURRENT.get() == this) CURRENT.remove(); // the next scenario on this thread cannot see this one
        return new SavedFiles(trace, keptVideo);
    }

    /**
     * Runs cleanup code and ignores failures.
     *
     * @param action the cleanup action
     */
    private static void quietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
