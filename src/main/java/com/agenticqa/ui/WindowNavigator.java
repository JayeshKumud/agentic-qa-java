package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Moves between the app's windows, injected into step classes by PicoContainer next to {@link Pages}:
 * <pre>
 * public WindowSteps(WindowNavigator windows, Pages pages) { ... }
 *
 * PricePage price = windows.switchToWindow("Add Master", "Price Window");   // from the home page
 * price.openFilter();
 * windows.switchToWindow("Price Window", "Customer Window");                // from the Price window
 * windows.switchToWindow("Price Window");                                   // back, already open
 * pages.price().openFilter();                                               // a later step, same window
 * </pre>
 * A switch clicks a button and one of its options in the current window, waits for the window that opens, checks
 * its title, maximizes it, and creates the page object for it ({@link #WINDOWS}). The new window becomes the
 * current one, so the next switch starts from it, and its page object is stored in the session, so
 * {@code pages.price()}, {@code pages.customers()} return it in later steps.
 * <p>
 * PicoContainer creates one navigator per scenario and gives the same instance to every step class, so the
 * current window is per scenario without a ThreadLocal.
 */
public class WindowNavigator {

    /** Name for {@link #switchToWindow(String)} of the scenario's first window, the home page. */
    public static final String HOME = "Home";

    /**
     * The windows {@link #switchToWindow(String, String)} can open: the option text (also the window's title) and
     * the page object for that window. Add a line here for each new window.
     */
    static final Map<String, Class<? extends PageComponents>> WINDOWS = Map.of(
            SharedElements.PRICE_STACK, PricePage.class,
            SharedElements.CUSTOMER_WINDOW, CustomersPage.class);

    private final TestSession session;
    private final Pages pages;
    /** Windows opened in this scenario, by option text. */
    private final Map<String, PageComponents> opened = new HashMap<>();
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
     * Opens a window from a button in the current window and switches to it, so the next steps act on it.
     * In the current window it clicks the button whose text is {@code button} (dropdownButton in
     * locators/common.json), then the option whose text is {@code option} (dropdownOption), waits for the new
     * window, checks that its title is {@code option}, maximizes it and makes the page fill it.
     * <p>
     * If that window is open already, it is only brought to the front; nothing is clicked.
     *
     * @param button the button's text in the current window, exactly as shown, for example "Add Master"
     * @param option the option's text, exactly as shown, which is also the new window's title,
     *               for example "Price Window"
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of the new window
     * @throws IllegalArgumentException if {@link #WINDOWS} has no page object for {@code option}
     * @throws AssertionError           if the new window's title is not {@code option}
     */
    public <T extends PageComponents> T switchToWindow(String button, String option) {
        PageComponents target = opened.get(option);
        if (target == null || target.elements().page().isClosed()) {
            Class<? extends PageComponents> type = WINDOWS.get(option);
            if (type == null) {
                throw new IllegalArgumentException("No page object for the window '" + option + "'. Add it to "
                        + "WindowNavigator.WINDOWS, for example \"" + option + "\", SomePage.class. Known: "
                        + WINDOWS.keySet());
            }
            target = keep(type, openWindow(current(), button, option, type));
            opened.put(option, target);
        }
        return makeCurrent(target);
    }

    /**
     * Switches back to a window opened earlier in this scenario, or to the first window with {@link #HOME}:
     * {@code windows.switchToWindow("Price Window")}, {@code windows.switchToWindow(WindowNavigator.HOME)}.
     *
     * @param window the option text the window was opened with, or {@link #HOME}
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of that window
     * @throws IllegalStateException if no window of that name is open in this scenario
     */
    public <T extends PageComponents> T switchToWindow(String window) {
        PageComponents target = HOME.equals(window) ? pages.home() : opened.get(window);
        if (target == null || target.elements().page().isClosed()) {
            throw new IllegalStateException("No open window '" + window + "'. Open it first with "
                    + "switchToWindow(button, \"" + window + "\"). Open: " + opened.keySet() + " and " + HOME);
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
     * Clicks the button and the option on {@code from}, waits for the window that opens and for its title to
     * become {@code option}, checks the title with TestNG, maximizes the window, makes the page fill it, and
     * creates the page object on it.
     */
    private static <T extends PageComponents> T openWindow(PageComponents from, String button, String option,
                                                          Class<T> type) {
        Page window = from.elements().page().waitForPopup(() -> from.selectDropdownOption(button, option));
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
