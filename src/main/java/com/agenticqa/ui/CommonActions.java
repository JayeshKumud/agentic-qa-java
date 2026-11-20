package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

/**
 * Actions that are the same on every page, called with the page (window) they should run on, so page objects
 * need no code for them:
 * <pre>
 * CommonActions.load(pricePage.page(), session.healer(), "Price");
 * </pre>
 * Their locators live once in {@code locators/common.json}. Pass the window the element is in: the home window
 * ({@code session.page()}) or a window the app opened (for example {@code pricePage.page()}).
 */
public final class CommonActions {

    private CommonActions() {}

    /**
     * Clicks the load button: the {@code button type="button"} whose span shows {@code name} (load in
     * locators/common.json). The name differs per window, for example "Price" in the Price window.
     *
     * @param page   the window to click it in
     * @param healer this scenario's healer ({@code session.healer()})
     * @param name   the text of the button's span, exactly as shown, for example "Price"
     */
    public static void load(Page page, Healer healer, String name) {
        new PageElements(page, healer, LocatorRepository.COMMON).get("load", "NAME", name).click();
    }
}
