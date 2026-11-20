package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

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
    private final Healer healer;
    private final PageElements el;

    /**
     * Wraps the scenario's browser page.
     *
     * @param page   the Playwright page
     * @param healer this scenario's healer
     */
    public HomePage(Page page, Healer healer) {
        this.page = page;
        this.healer = healer;
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
        Page newWindow = page.waitForPopup(() -> selectAddMasterOption("TestWINDOW"));
        newWindow.waitForLoadState();
        return newWindow;
    }

    /**
     * Opens the Add Master dropdown and clicks one of its options: the {@code li role=option} whose span shows
     * {@code option}. The dropdown can close again right after it opens, so it waits for the option list to be
     * visible and opens the dropdown once more if the first click on the option fails.
     *
     * @param option the text of the option, for example "TestWINDOW"
     * @return this page
     */
    public HomePage selectAddMasterOption(String option) {
        try {
            showAddMasterOptions();
            el.get("addMasterOption", "MYTEST", option).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (PlaywrightException closedTooSoon) {
            showAddMasterOptions();
            el.get("addMasterOption", "MYTEST", option).click();
        }
        return this;
    }

    /**
     * Opens the Price window from the Add Master dropdown: clicks the option, waits for the new window to load
     * and for its title to become the option, checks the title, then maximizes the window and makes the page fill it.
     *
     * @param option the Add Master option, which is also the title of the window it opens, for example "Price"
     * @return the Price window
     * @throws AssertionError if the window's title is not {@code option}
     */
    public PricePage openAddMasterMenu(String option) {
        Page window = page.waitForPopup(() -> selectAddMasterOption(option));
        window.waitForLoadState();
        PricePage pricePage = new PricePage(window, healer);
        waitForTitle(window, option);
        Assert.assertEquals(pricePage.getTitle(), option, "title of the window the Add Master option opened");
        pricePage.maximize().resize();
        return pricePage;
    }

    /**
     * Waits until the window's title is {@code title}. The new window first loads the app's start page, which has
     * the home page's title; the app's script sets the real title a moment after the load event, so reading the
     * title right after waitForLoadState still gives the old one. Gives up after ui.waits.assertionMs and leaves
     * the failure message to the Assert that follows.
     *
     * @param window the new window
     * @param title  the title to wait for
     */
    private static void waitForTitle(Page window, String title) {
        try {
            window.waitForFunction("t => document.title === t", title,
                    new Page.WaitForFunctionOptions().setTimeout(Settings.getAssertionMs()));
        } catch (PlaywrightException stillOtherTitle) {
            // the Assert reports the title it found
        }
    }

    /** Clicks Add Master unless its option list is already open, then waits until the list is visible. */
    private void showAddMasterOptions() {
        Locator menu = el.get("addMasterMenu");
        if (!menu.isVisible()) el.get("addMasterButton").click();
        menu.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
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
