package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

/**
 * Buttons that are the same on every page, clicked in the window passed in, so page objects need no code for
 * them (moving between windows is {@link WindowNavigator}'s job):
 * <pre>
 * Page help = CommonActions.openHelp(session.page(), session.healer());  // opens help in a new page
 * CommonActions.openKeyboardShortcuts(pricePage.page(), session.healer()); // Price window
 * </pre>
 * Their locators live once in {@code locators/common.json}. Lookups go through the scenario's healer like every
 * other element, so a button that is not found is listed in the failure report with its selector.
 */
public final class CommonActions {

    /** Text of the log out button (textButton in locators/common.json). */
    public static final String LOG_OUT = "Log Out";

    private CommonActions() {}

    /**
     * Clicks the help button (helpButton in locators/common.json), which opens help in a new browser page, and
     * waits until that page has loaded.
     *
     * @param page   the window to click it in
     * @param healer this scenario's healer ({@code session.healer()})
     * @return the help page; the window it was opened from stays as it was
     */
    public static Page openHelp(Page page, Healer healer) {
        Page help = page.waitForPopup(() -> common(page, healer).get("helpButton").click());
        help.waitForLoadState();
        return help;
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
     * Logs out: clicks the log out button (textButton in locators/common.json with TEXT {@value #LOG_OUT}: the
     * {@code button type="button" data-testid="button"} whose {@code div} shows it), then checks that the login
     * page is shown in that window ({@link LoginPage#verifyDisplayed()}).
     *
     * @param page   the window to click it in
     * @param healer this scenario's healer ({@code session.healer()})
     * @return the login page, already checked
     * @throws AssertionError if the login page is not shown after log out
     */
    public static LoginPage logout(Page page, Healer healer) {
        common(page, healer).get("textButton", "TEXT", LOG_OUT).click();
        return new LoginPage(page, healer).verifyDisplayed();
    }

    /**
     * Waits until an element is visible, up to ui.waits.assertionMs, and fails with a clear TestNG message (not a
     * Playwright timeout) if it does not appear: {@code CommonActions.assertVisible(button, "the COE role button")}.
     *
     * @param element the element that must be shown
     * @param what    what it is, for the failure message
     * @throws AssertionError if the element is not visible in time
     */
    public static void assertVisible(Locator element, String what) {
        try {
            element.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(Settings.getAssertionMs()));
        } catch (PlaywrightException notShown) {
            Assert.fail("Expected " + what + " to be shown within " + Settings.getAssertionMs() + " ms, on "
                    + element.page().url());
        }
    }

    private static PageElements common(Page page, Healer healer) {
        return new PageElements(page, healer, LocatorRepository.COMMON);
    }
}
