package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Moves between the app's windows, injected into step classes by PicoContainer next to {@link Pages}:
 * <pre>
 * public WindowSteps(WindowNavigator windows, Pages pages) { ... }
 *
 * PricePage price = windows.switchToWindow(Window.PRICE_STACK);    // opens it from the home page
 * price.openFilter();
 * windows.switchToWindow(Window.CUSTOMER_WINDOW);                   // opens it from Price Stack
 * windows.switchToWindow(Window.PRICE_STACK);                       // back, already open
 * windows.switchToWindow(Window.HOME);                              // the first window
 * pages.price().openFilter();                                                    // a later step, same window
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
     * {@code windows.switchToWindow(Window.PRICE_STACK)}.
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
     * to become {@code option}, checks the title with TestNG, maximizes the window, makes the page fill it, and
     * creates the page object on it.
     */
    private static <T extends PageComponents> T openWindow(PageComponents from, String option, Class<T> type) {
        Page window = from.elements().page().waitForPopup(() -> from.selectDropdownOption(option));
        window.waitForLoadState();
        CommonActions.assertTitle(window, option);
        BrowserWindow.maximize(window);
        BrowserWindow.resize(window);
        return create(type, window, from.elements().healer());
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
