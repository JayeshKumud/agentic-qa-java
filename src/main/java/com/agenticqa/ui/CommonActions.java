package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

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

    private static PageElements common(Page page, Healer healer) {
        return new PageElements(page, healer, LocatorRepository.COMMON);
    }
}
