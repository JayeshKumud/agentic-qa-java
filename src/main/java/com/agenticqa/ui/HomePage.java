package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

/**
 * Page object of the home page. Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/homepage.json}, not in this class, and elements are
 * looked up by name when they are used ({@code el.get("roleTitle")}).
 */
public class HomePage {

    /** Name of the locator file, locators/homepage.json. */
    public static final String LOCATORS = "homepage";

    private final Page page;
    private final PageElements el;

    /**
     * Wraps the scenario's browser page.
     *
     * @param page   the Playwright page
     * @param healer this scenario's healer
     */
    public HomePage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /**
     * Clicks the Select button of a role in the change-role dialog. Each button carries the class
     * {@code role_login_<ROLE>}, so the role picks its own button directly, without reading table rows.
     *
     * @param role the role as shown in the Role column, for example "COE", "NBE" or "OEM"
     * @return this page
     */
    public HomePage selectRole(String role) {
        el.get("roleLogin", "ROLE", role).click();
        return this;
    }
}
