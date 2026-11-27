package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves between the app's windows, injected into step classes by PicoContainer next to {@link Pages}:
 * <pre>
 * public WindowSteps(WindowNavigator windows, Pages pages) { ... }
 *
 * PricePage price = windows.switchToWindow(Window.PRICE_BOOKING);  // opens it from the home page
 * price.openFilter();
 * windows.switchToWindow(Window.CUSTOMER_WINDOW);                 // opens it from Price Booking
 * windows.switchToWindow(Window.PRICE_BOOKING);                   // back, already open
 * windows.switchToWindow(Window.HOME);                            // the first window
 * pages.price().openFilter();                                      // a later step, same window
 * </pre>
 * Every window has one dropdown button (found by its data-testid), so a window is opened by its option only: the
 * current window's dropdown button is clicked, then the option. The new window becomes the current one, so the
 * next switch starts from it, and its page object is stored in the session, so {@code pages.price()},
 * {@code pages.customers()} return it in later steps.
 * <p>
 * PicoContainer creates one navigator per scenario and gives the same instance to every step class, so the
 * current window is per scenario without a ThreadLocal.
 */
public class WindowNavigator {

    private static final Logger log = LoggerFactory.getLogger(WindowNavigator.class);

    private final TestSession session;
    private final Pages pages;
    /** Windows opened in this scenario. */
    private final Map<Window, PageComponents> opened = new EnumMap<>(Window.class);
    /** The window steps are on; null means the home page. */
    private PageComponents current;

    /**
     * Receives the scenario's session and page objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param pages   the scenario's page objects
     */
    public WindowNavigator(TestSession session, Pages pages) {
        this.session = session;
        this.pages = pages;
    }

    /**
     * Switches to a window, opening it if needed, so the next steps act on it:
     * {@code windows.switchToWindow(Window.PRICE_BOOKING)}.
     * <ul>
     *   <li>{@link Window#HOME}: the scenario's first window.</li>
     *   <li>A window opened earlier in this scenario: brought to the front; nothing is clicked.</li>
     *   <li>Otherwise it is opened from the current window: its dropdown button is clicked, then the option
     *       {@link Window#text()}; the new window's title must be that text, then it is maximized and its page
     *       fills it. Its page object is {@link Window#page()}.</li>
     * </ul>
     *
     * @param window the window
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of that window
     * @throws AssertionError if a new window's title is not the window's text
     */
    public <T extends PageComponents> T switchToWindow(Window window) {
        if (window == Window.HOME) return makeCurrent(pages.home());
        PageComponents target = opened.get(window);
        if (target == null || target.elements().page().isClosed()) {
            target = keep(window.page(), openWindow(current(), window.text(), window.page()));
            Page shown = target.elements().page();
            // the app may load the new window into the window it was opened from (Price -> Unique in the same
            // browser window): that window no longer shows its old page, so forget it; switching back opens it again
            opened.values().removeIf(old -> old.elements().page() == shown);
            opened.put(window, target);
        }
        return makeCurrent(target);
    }

    /** @return the page object of the window steps are on: the last one switched to, else the home page */
    public PageComponents current() {
        return current != null ? current : pages.home();
    }

    @SuppressWarnings("unchecked")
    private <T extends PageComponents> T makeCurrent(PageComponents target) {
        target.elements().page().bringToFront();
        current = target;
        return (T) target;
    }

    /** Stores the page object of an opened window in the session, so pages.get(type) returns it. */
    private <T extends PageComponents> T keep(Class<T> type, PageComponents page) {
        return session.putPageComponent(type, type.cast(page));
    }

    /**
     * Clicks the dropdown button and the option on {@code from}, waits for the window that opens and for its title
     * to become {@code option}, checks the title with TestNG, maximizes the window, makes the page fill it, creates
     * the page object on it and waits until that page is ready ({@link PageComponents#verifyDisplayed()}).
     * <p>
     * It does not rely on {@code from} being the new window's opener ({@code page.waitForPopup}), because apps open
     * windows in three ways:
     * <ol>
     *   <li>{@code from} opens it, or another window does (for example the home window opens every window): a new
     *       page appears in the browser context, whichever window opened it.</li>
     *   <li>the app reuses a window that is already open ({@code window.open(url, name)}), or loads the new page
     *       into {@code from} itself (Price Booking becomes Unique Window in the same browser window): no new page
     *       appears, so the window is found by its title {@code option}. In the second case {@code from}'s page
     *       object is forgotten, so switching back to it opens it again.</li>
     *   <li>the option is clicked before {@code from} is ready, or the app opens the window only after a slow call:
     *       {@code from} is checked first ({@link PageComponents#verifyDisplayed()}) and the wait lasts up to
     *       ui.waits.navigationMs.</li>
     * </ol>
     */
    private static <T extends PageComponents> T openWindow(PageComponents from, String option, Class<T> type) {
        from.verifyDisplayed();
        Page window = clickAndFindWindow(from, option);
        window.waitForLoadState();
        CommonActions.assertTitle(window, option);
        BrowserWindow.maximize(window);
        BrowserWindow.resize(window);
        T pageObject = create(type, window, from.elements().healer());
        pageObject.verifyDisplayed();   // the window's own "ready" check, for example PricePage: Search Data button
        return pageObject;
    }

    /**
     * Clicks the option on {@code from} and returns the window it opened: the first page that was not in the browser
     * context before the click, else an already open window (other than {@code from}) whose title is
     * {@code option}, else {@code from} itself once its title is {@code option} (the page was replaced in place). Checks every 250 ms, up to ui.waits.navigationMs.
     *
     * @throws AssertionError if neither shows up in time; the message lists the open windows
     */
    private static Page clickAndFindWindow(PageComponents from, String option) {
        Page source = from.window();
        BrowserContext context = source.context();
        Set<Page> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(context.pages());
        String sourceTitle = titleOf(source);

        from.selectDropdownOption(option);

        long end = System.currentTimeMillis() + Settings.getNavigationMs();
        while (true) {
            for (Page page : context.pages()) {
                if (!before.contains(page)) {
                    log.info("'{}' opened a new window from '{}'", option, sourceTitle);
                    return page;
                }
            }
            for (Page page : context.pages()) {
                if (page != source && !page.isClosed() && option.equals(titleOf(page))) {
                    log.info("'{}' is an already open window (reused), not a new one", option);
                    return page;
                }
            }
            if (option.equals(titleOf(source))) {
                log.info("'{}' replaced '{}' in the same window", option, sourceTitle);
                return source;
            }
            if (System.currentTimeMillis() >= end) {
                List<String> open = context.pages().stream().map(WindowNavigator::titleOf).toList();
                throw new AssertionError("Clicked '" + option + "' on '" + sourceTitle + "', but within "
                        + Settings.getNavigationMs() + " ms no new window opened and no open window has the title '"
                        + option + "'. Open windows: " + open);
            }
            source.waitForTimeout(250);   // lets Playwright process the browser's events while waiting
        }
    }

    /** The page's title, or "" while it is loading or closing. */
    private static String titleOf(Page page) {
        try {
            return page.title();
        } catch (PlaywrightException loadingOrClosed) {
            return "";
        }
    }

    /** Creates a page object on the given window with its (Page, Healer) constructor. */
    private static <T extends PageComponents> T create(Class<T> type, Page window, Healer healer) {
        try {
            return type.getConstructor(Page.class, Healer.class).newInstance(window, healer);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getSimpleName()
                    + " needs a public constructor (Page page, Healer healer)", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("The constructor of " + type.getSimpleName() + " failed: "
                    + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create " + type.getSimpleName() + ": " + e, e);
        }
    }
}
