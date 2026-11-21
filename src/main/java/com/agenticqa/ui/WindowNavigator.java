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
 * PricePage price = windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK);
 * price.openFilter();
 * windows.switchToWindow(DropdownButtonText.PRICE_STACK, DropdownOptionValue.CUSTOMER_WINDOW);  // from Price
 * windows.switchToWindow(DropdownOptionValue.PRICE_STACK);                                     // back, already open
 * windows.switchToWindow("Add tool", "Price Stack");                                    // texts, from a feature
 * pages.price().openFilter();                                               // a later step, same window
 * </pre>
 * A switch clicks a button and one of its options in the current window, waits for the window that opens, checks
 * its title, maximizes it, and creates the page object for it ({@link DropdownOptionValue#window()}). The new window becomes the
 * current one, so the next switch starts from it, and its page object is stored in the session, so
 * {@code pages.price()}, {@code pages.customers()} return it in later steps.
 * <p>
 * PicoContainer creates one navigator per scenario and gives the same instance to every step class, so the
 * current window is per scenario without a ThreadLocal.
 */
public class WindowNavigator {

    /** Name for {@link #switchToWindow(String)} of the scenario's first window, the home page. */
    public static final String HOME = "Home";

    private final TestSession session;
    private final Pages pages;
    /** Windows opened in this scenario, by the option that opened them. */
    private final Map<DropdownOptionValue, PageComponents> opened = new EnumMap<>(DropdownOptionValue.class);
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
     * Opens a window from a dropdown in the current window and switches to it, so the next steps act on it:
     * {@code windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK)}. In the current
     * window it clicks the dropdown's button, then the option, waits for the new window, checks that its title is
     * the option's text, maximizes it and makes the page fill it; {@link DropdownOptionValue#window()} gives its
     * page object.
     * <p>
     * If that window is open already, it is only brought to the front; nothing is clicked.
     *
     * @param button the dropdown button clicked in the current window, for example DropdownButtonText.ADD_TOOL
     * @param option the option that opens the window, for example DropdownOptionValue.PRICE_STACK
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of the new window
     * @throws AssertionError if the new window's title is not the option's text
     */
    public <T extends PageComponents> T switchToWindow(DropdownButtonText button, DropdownOptionValue option) {
        PageComponents target = opened.get(option);
        if (target == null || target.elements().page().isClosed()) {
            Class<? extends PageComponents> type = option.window();
            target = keep(type, openWindow(current(), button.text(), option.text(), type));
            opened.put(option, target);
        }
        return makeCurrent(target);
    }

    /**
     * {@link #switchToWindow(DropdownButtonText, DropdownOptionValue)} with the texts as shown, for steps that get them from a feature
     * file: {@code windows.switchToWindow("Add tool", "Price Stack")}.
     *
     * @param button the dropdown button's text, exactly as shown
     * @param option the option's text, exactly as shown
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of the new window
     * @throws IllegalArgumentException if a text is not in {@link DropdownButtonText} or {@link DropdownOptionValue}
     *                                  (the message lists the known ones)
     */
    public <T extends PageComponents> T switchToWindow(String button, String option) {
        return switchToWindow(DropdownButtonText.of(button), DropdownOptionValue.of(option));
    }

    /**
     * Switches back to a window opened earlier in this scenario:
     * {@code windows.switchToWindow(DropdownOptionValue.PRICE_STACK)}.
     *
     * @param window the option the window was opened with
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of that window
     * @throws IllegalStateException if that window is not open in this scenario
     */
    public <T extends PageComponents> T switchToWindow(DropdownOptionValue window) {
        PageComponents target = opened.get(window);
        if (target == null || target.elements().page().isClosed()) {
            throw new IllegalStateException("No open window '" + window + "'. Open it first with "
                    + "switchToWindow(button, DropdownOptionValue." + window.name() + "). Open: " + opened.keySet()
                    + " and " + HOME);
        }
        return makeCurrent(target);
    }

    /**
     * Switches back to a window opened earlier in this scenario, or to the first window with {@link #HOME}, by
     * its text: {@code windows.switchToWindow("Price Stack")}, {@code windows.switchToWindow(WindowNavigator.HOME)}.
     *
     * @param window the option text the window was opened with, or {@link #HOME}
     * @param <T>    the page type, taken from the variable it is assigned to
     * @return the page object of that window
     * @throws IllegalStateException if no window of that name is open in this scenario
     */
    public <T extends PageComponents> T switchToWindow(String window) {
        return HOME.equals(window) ? makeCurrent(pages.home()) : switchToWindow(DropdownOptionValue.of(window));
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
