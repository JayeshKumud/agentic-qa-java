package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Page object of the Price window, opened with {@code windows.switchToWindow("Add tool", "Price Stack")}
 * ({@link WindowNavigator}). Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/pricepage.json}, not in this class, and elements are
 * looked up by name when they are used ({@code el.get("name")}).
 */
public class PricePage implements PageComponents {

    /** Name of the locator file, locators/pricepage.json. */
    public static final String LOCATORS = "pricepage";

    /**
     * The browser page title (the tab) of the Price window: the text of the option that opens it
     * ({@link DropdownOptionValue#PRICE_STACK}), because {@link WindowNavigator} checks the new window's title against it.
     */
    public static final String PAGE_TITLE = DropdownOptionValue.PRICE_STACK.text();

    private final Page page;
    private final PageElements el;

    /**
     * Wraps the Price window.
     *
     * @param page   the Playwright page of the new window
     * @param healer this scenario's healer
     */
    public PricePage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /** @return the title of the Price window */
    @Override
    public String getTitle() {
        return page.title();
    }

    /** @return this page's elements, used by the shared actions of {@link PageComponents} such as selectDropdownOption */
    @Override
    public PageElements elements() {
        return el;
    }

    /** @return the Playwright page of the Price window, for anything this page object does not offer yet */
    public Page page() {
        return page;
    }

    /**
     * Clicks the filter button of the Price window.
     *
     * @return this page
     */
    public PricePage openFilter() {
        el.get("filterButton").click();
        return this;
    }

    /**
     * Counts the grid rows ({@code div[role='row']}) after waiting until the first one is visible, because the
     * grid fills in after the window has loaded and a count does not wait by itself.
     *
     * @return the number of rows, a header row included if the grid marks it role=row
     */
    public int rowCount() {
        Locator rows = el.get("rows");
        rows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return rows.count();
    }

    /**
     * Checks that the Price window is shown: its browser page title is {@link #PAGE_TITLE}.
     *
     * @return this page
     * @throws AssertionError if the title is not {@link #PAGE_TITLE} within ui.waits.assertionMs
     */
    public PricePage verifyDisplayed() {
        CommonActions.assertTitle(page, PAGE_TITLE);
        return this;
    }

    /**
     * Maximizes the window on the screen, like its maximize button ({@link BrowserWindow#maximize}).
     *
     * @return this page
     */
    public PricePage maximize() {
        BrowserWindow.maximize(page);
        return this;
    }

    /**
     * Makes the page fill its window ({@link BrowserWindow#resize}).
     *
     * @return this page
     */
    public PricePage resize() {
        BrowserWindow.resize(page);
        return this;
    }
}
