package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Page object of the Price window, the new window an Add Master option opens
 * ({@link HomePage#openAddMasterMenu(String)}). Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/pricepage.json}, not in this class, and elements are
 * looked up by name when they are used ({@code el.get("name")}).
 */
public class PricePage implements PageComponent {

    /** Name of the locator file, locators/pricepage.json. */
    public static final String LOCATORS = "pricepage";

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

    /** @return the Playwright page of the Price window, for anything this page object does not offer yet */
    public Page page() {
        return page;
    }

    /**
     * Types a text into the filter input of the Price window.
     *
     * @param text what to filter on
     * @return this page
     */
    public PricePage filter(String text) {
        el.get("filterInput").fill(text);
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
