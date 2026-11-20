package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.testng.Assert;

import java.lang.reflect.InvocationTargetException;

/**
 * Actions that are the same on every page, so page objects need no code for them: opening a window from a
 * dropdown ({@link #openWindow}), and buttons clicked in the window passed in:
 * <pre>
 * CommonActions.openHelp(session.page(), session.healer());               // home window
 * CommonActions.openKeyboardShortcuts(pricePage.page(), session.healer()); // Price window
 * </pre>
 * Their locators live once in {@code locators/common.json}. Lookups go through the scenario's healer like every
 * other element, so a button that is not found is listed in the failure report with its selector.
 */
public final class CommonActions {

    private CommonActions() {}

    /**
     * Clicks the help button (helpButton in locators/common.json).
     *
     * @param page   the window to click it in
     * @param healer this scenario's healer ({@code session.healer()})
     */
    public static void openHelp(Page page, Healer healer) {
        common(page, healer).get("helpButton").click();
    }

    /**
     * Clicks the keyboard shortcuts button (keyboardShortcutsButton in locators/common.json).
     *
     * @param page   the window to click it in
     * @param healer this scenario's healer ({@code session.healer()})
     */
    public static void openKeyboardShortcuts(Page page, Healer healer) {
        common(page, healer).get("keyboardShortcutsButton").click();
    }

    /**
     * Opens a window from a dropdown and returns the page object for it: clicks the dropdown's button and the
     * option on {@code from}, waits for the new window to load and for its title to become {@code option}, checks
     * the title with TestNG, then maximizes the window and makes the page fill it.
     * <pre>
     * PricePage price = CommonActions.openWindow(home, "Add Master", "Price", PricePage.class);
     * KlmPage klm     = CommonActions.openWindow(home, "Add Master", "Klm", KlmPage.class);
     * </pre>
     * The page class needs the usual {@code (Page, Healer)} constructor; it is created on the new window.
     *
     * @param from     the page object whose dropdown opens the window, for example the home page
     * @param dropdown the text of the dropdown's button, for example "Add Master"
     * @param option   the option, which is also the title of the window it opens, for example "Price"
     * @param type     the page object of the new window, for example PricePage.class
     * @param <T>      the page type
     * @return the page object, bound to the new window
     * @throws AssertionError if the window's title is not {@code option}
     */
    public static <T extends PageComponent> T openWindow(PageComponent from, String dropdown, String option,
                                                         Class<T> type) {
        Page window = from.elements().page().waitForPopup(() -> from.selectDropdownOption(dropdown, option));
        window.waitForLoadState();
        waitForTitle(window, option);
        Assert.assertEquals(window.title(), option, "title of the window the " + dropdown + " option opened");
        BrowserWindow.maximize(window);
        BrowserWindow.resize(window);
        return create(type, window, from.elements().healer());
    }

    /**
     * Waits until the window's title is {@code title}. The new window first loads the app's start page, which has
     * the opener's title; the app's script sets the real title a moment after the load event, so reading the
     * title right after waitForLoadState still gives the old one. Gives up after ui.waits.assertionMs and leaves
     * the failure message to the Assert that follows.
     */
    private static void waitForTitle(Page window, String title) {
        try {
            window.waitForFunction("t => document.title === t", title,
                    new Page.WaitForFunctionOptions().setTimeout(Settings.getAssertionMs()));
        } catch (PlaywrightException stillOtherTitle) {
            // the Assert reports the title it found
        }
    }

    /** Creates a page object on the given window with its (Page, Healer) constructor. */
    private static <T extends PageComponent> T create(Class<T> type, Page window, Healer healer) {
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

    private static PageElements common(Page page, Healer healer) {
        return new PageElements(page, healer, LocatorRepository.COMMON);
    }
}
