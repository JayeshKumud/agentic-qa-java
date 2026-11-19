package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page object of the home page. Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/homepage.json}, not in this class, and elements are
 * looked up by name when they are used ({@code el.get("roleTitle")}).
 */
public class HomePage implements PageComponent {

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

    /** @return the title of the browser page */
    @Override
    public String getTitle() {
        return page.title();
    }

    /**
     * Clicks the Select button of a role in the change-role dialog. Each button carries the class
     * {@code role_login_<ROLE>}, so the role picks its own button directly, without reading table rows.
     * <p>
     * After the click it waits until the page has finished loading, so the next step sees the page of the new
     * role.
     *
     * @param role the role as shown in the Role column, for example "COE", "NBE" or "OEM"
     * @return this page
     */
    public HomePage selectRole(String role) {
        clickAndWait(el.get("roleLogin", "ROLE", role));
        return this;
    }

    /**
     * Clicks the button in the Select column of the row whose Name column (first column) is {@code name}.
     * The button carries the class {@code role_login_<name>}, so the name picks its button directly.
     *
     * @param name the value in the Name column, for example "COE"
     * @return this page
     */
    public HomePage selectByName(String name) {
        clickAndWait(el.get("selectButtonByName", "MYNAME", name));
        return this;
    }

    /**
     * Clicks Add Master, then the TestWINDOW option, which opens a new window, and waits until it has loaded.
     *
     * @return the new window; find its elements with {@code newWindow.locator(...)}
     */
    public Page openTestWindow() {
        el.get("addMasterButton").click();
        Page newWindow = page.waitForPopup(() -> el.get("dropdownOption", "OPTION", "TestWINDOW").click());
        newWindow.waitForLoadState();
        return newWindow;
    }

    /**
     * Clicks an element that loads a new page and waits until that page has loaded, as in CustomersPage.
     * The click waits for the navigation it starts; waitForLoadState then waits for the new document's load event.
     *
     * @param element the element to click
     */
    private void clickAndWait(Locator element) {
        element.click();
        page.waitForLoadState();
    }
}
