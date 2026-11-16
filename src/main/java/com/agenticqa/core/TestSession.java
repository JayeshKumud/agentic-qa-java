package com.agenticqa.core;

import com.agenticqa.api.ApiCall;
import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;
import com.agenticqa.ui.PageComponent;
import com.agenticqa.ui.healing.HealEvent;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything one scenario needs while it runs: the browser page, the API access and a typed store for sharing data
 * between steps. Cucumber's PicoContainer creates one instance per scenario and injects it into every step class,
 * so parallel scenarios never share a browser or data.
 * <p>
 * The page and the API calls come from the same browser context, so they share cookies and login state.
 * The context is created lazily, the first time a step asks for the page or the API, in the browser of the
 * current worker thread ({@link BrowserPool}); closing the session closes the context, not the browser.
 * <p>
 * Thread safety: a scenario runs on one thread from its first hook to its last, and nothing in a session is
 * shared with other scenarios, so parallel scenarios are isolated by construction.
 */
public class TestSession {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * The session of the scenario running on each thread. It points to the session PicoContainer created (Hooks
     * binds it before every scenario, {@link #close} removes it), so static code and injected code see the same
     * session. It is the only ThreadLocal of the session: per-scenario data lives in the session's own fields and
     * store, so nothing else has to be cleared.
     */
    private static final ThreadLocal<TestSession> CURRENT = new ThreadLocal<>();

    /** Where Playwright writes videos while recording; kept videos are copied to target/artifacts/videos. */
    private static final Path VIDEO_RECORDING_DIR = Paths.get("target", "artifacts", "videos", "recording");

    /**
     * Files the session kept when it closed.
     *
     * @param trace the Playwright trace, or null if none was kept
     * @param video the video of the page, or null if none was kept
     */
    public record SavedFiles(Path trace, Path video) {}

    private final Settings settings = EnvConfig.get();
    private final Map<DataKey<?>, Object> store = new HashMap<>();
    private final List<ApiCall> apiCalls = new ArrayList<>();
    private final Map<Class<? extends PageComponent>, PageComponent> pageComponents = new HashMap<>();

    private BrowserContext context;
    private Page page;
    private Healer healer;
    private String scenarioName = "scenario";

    /**
     * Settings of the active environment.
     *
     * @return the typed settings
     */
    public Settings settings() {
        return settings;
    }

    /**
     * Remembers the scenario name; it is used to name the trace file when the scenario fails.
     *
     * @param name the Cucumber scenario name
     */
    public void scenarioName(String name) {
        this.scenarioName = name;
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
        if (healer == null) healer = new Healer(settings.healing());
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
     * API access that shares cookies with the browser. Requests need the full URL (api.baseUrl plus the path),
     * because the underlying context is configured with the UI base URL.
     *
     * @return the API request context of this scenario
     */
    public synchronized APIRequestContext api() {
        return context().request();
    }

    /**
     * Adds an HTTP call to this scenario's call log. Called by every API client.
     *
     * @param call the finished call
     */
    public synchronized void recordApiCall(ApiCall call) {
        apiCalls.add(call);
    }

    /**
     * HTTP calls of this scenario so far, oldest first.
     *
     * @return a copy of the call log
     */
    public synchronized List<ApiCall> apiCalls() {
        return List.copyOf(apiCalls);
    }

    /**
     * Stores a value for later steps of the same scenario.
     *
     * @param key   the typed key
     *
     * @param value the value to keep
     * @param <T>   the type of the value
     */
    public <T> void put(DataKey<T> key, T value) {
        store.put(key, value);
    }

    /**
     * Reads a stored value.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return the stored value
     * @throws IllegalStateException if nothing was stored under the key in this scenario
     */
    public <T> T get(DataKey<T> key) {
        Object value = store.get(key);
        if (value == null) {
            throw new IllegalStateException("Nothing stored under key '" + key.name()
                    + "' in this scenario. Did an earlier step run?");
        }
        return key.type().cast(value);
    }

    /**
     * Tells whether a value is stored.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return true if a value was stored
     */
    public <T> boolean has(DataKey<T> key) {
        return store.containsKey(key);
    }

    /**
     * Adds an item to a list stored under a list key, creating the list on first use:
     * {@code session.add(Keys.CREATED_INST_MAN, instMan)}.
     *
     * @param key  a list key, see {@link DataKey#listOf}
     * @param item the item to add
     * @param <E>  the type of the list elements
     * @throws IllegalArgumentException if the item is null
     */
    public synchronized <E> void add(DataKey<List<E>> key, E item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add null to '" + key.name() + "' in this scenario");
        }
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) store.computeIfAbsent(key, k -> new ArrayList<E>());
        list.add(item);
    }

    /**
     * The items added under a list key, oldest first, as a copy.
     *
     * @param key a list key, see {@link DataKey#listOf}
     * @param <E> the type of the list elements
     * @return the items, empty if none were added
     */
    public synchronized <E> List<E> list(DataKey<List<E>> key) {
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) store.get(key);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * The items added under a list key, for steps that need at least one.
     *
     * @param key a list key, see {@link DataKey#listOf}
     * @param <E> the type of the list elements
     * @return the items, oldest first, as a copy
     * @throws IllegalStateException if nothing was added under the key in this scenario
     */
    public <E> List<E> requireList(DataKey<List<E>> key) {
        List<E> list = list(key);
        if (list.isEmpty()) {
            throw new IllegalStateException("Nothing was added to '" + key.name()
                    + "' in this scenario. Did an earlier step run?");
        }
        return list;
    }

    /**
     * Makes this the current session of this thread, so {@link #current()} finds it. Hooks calls it before every
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
                    + " inside a scenario, after Hooks.before has bound the session, and only on the scenario's thread.");
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
     * Used by {@code PageComponentFactory}; the pages are dropped with the session.
     *
     * @param type   the page class, for example HomePage.class
     * @param create creates the page when this scenario has none of that type yet
     * @param <T>    the page type
     * @return the scenario's instance of that page
     */
    public synchronized <T extends PageComponent> T pageComponent(Class<T> type, Supplier<T> create) {
        PageComponent existing = pageComponents.get(type);
        if (existing == null) {
            existing = create.get();
            pageComponents.put(type, existing);
        }
        return type.cast(existing);
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
        Settings.Ui ui = settings.ui();
        Browser browser = BrowserPool.browser(ui);
        boolean maximized = BrowserPool.maximized(ui);
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(ui.baseUrl())
                .setIgnoreHTTPSErrors(ui.ignoreHttpsErrors());
        if (maximized) {
            options.setViewportSize(null); // the page uses the real, maximized window size
        } else {
            options.setViewportSize(ui.viewport().width(), ui.viewport().height());
        }
        if (ui.recordsVideo()) {
            options.setRecordVideoDir(VIDEO_RECORDING_DIR);
            if (!maximized) {
                options.setRecordVideoSize(ui.viewport().width(), ui.viewport().height()); // full size, not scaled down
            }
        }
        context = browser.newContext(options);
        Settings.Waits waits = ui.waits();
        context.setDefaultTimeout(waits.actionMs());
        context.setDefaultNavigationTimeout(waits.navigationMs());
        PlaywrightAssertions.setDefaultAssertionTimeout(waits.assertionMs()); // assertThat would otherwise use 5 s
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(false));
    }

    /**
     * Stops tracing (the zip is kept only when the scenario failed) and closes the context. The page's video is
     * kept when {@code ui.video} is always, or failed and the scenario failed; otherwise it is deleted. The
     * browser stays open for the next scenario on this thread.
     * Each close is attempted separately so cleanup never hides the real test result.
     *
     * @param failed true if the scenario failed
     * @return the kept trace and video; both null if nothing was kept (passed, or no browser was started)
     */
    public synchronized SavedFiles close(boolean failed) {
        Path trace = null;
        if (context != null) {
            try {
                if (failed) {
                    Path file = artifactFile("traces", ".zip");
                    context.tracing().stop(new Tracing.StopOptions().setPath(file));
                    System.out.println("Trace saved: " + file);
                    trace = file;
                } else {
                    context.tracing().stop();
                }
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        Video video = null;
        try {
            if (page != null) video = page.video(); // null when video is off
        } catch (RuntimeException ignored) {
            // best effort
        }
        quietly(() -> { if (context != null) context.close(); }); // closing finishes writing the video
        Path keptVideo = video == null ? null : keepOrDelete(video, failed);
        context = null;
        page = null;
        pageComponents.clear();
        if (CURRENT.get() == this) CURRENT.remove(); // the next scenario on this thread cannot see this one
        return new SavedFiles(trace, keptVideo);
    }

    /**
     * Copies a finished video to target/artifacts/videos when it is to be kept, then deletes the recording.
     *
     * @param video  the page's video
     * @param failed true if the scenario failed
     * @return the kept video file, or null if none was kept
     */
    private Path keepOrDelete(Video video, boolean failed) {
        Path kept = null;
        if (failed || settings.ui().video().equals("always")) {
            try {
                Path file = artifactFile("videos", ".webm");
                video.saveAs(file);
                System.out.println("Video saved: " + file);
                kept = file;
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        quietly(video::delete); // the recording under videos/recording
        return kept;
    }

    /**
     * Path of a file of this scenario, for example
     * target/artifacts/traces/2026-11-09_14-05-09_create-and-find-a-customer_TestNG-1.zip
     *
     * @param folder    the folder under target/artifacts, for example traces or videos
     * @param extension the file extension with its dot, for example .zip
     * @return the file path
     */
    private Path artifactFile(String folder, String extension) {
        String slug = scenarioName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        // thread name keeps the files of parallel (or retried) scenarios with the same name apart
        String thread = Thread.currentThread().getName().replaceAll("[^A-Za-z0-9]+", "-");
        return Paths.get("target", "artifacts", folder,
                LocalDateTime.now().format(FILE_STAMP) + "_" + slug + "_" + thread + extension);
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
