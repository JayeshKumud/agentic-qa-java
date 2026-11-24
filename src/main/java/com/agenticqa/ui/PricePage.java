package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;
import java.util.Map;

/**
 * Page object of the Price window, opened with {@code windows.switchToWindow(Window.PRICE_BOOKING)}
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
     * ({@link Window#PRICE_BOOKING}), because {@link WindowNavigator} checks the new window's title against it.
     */
    public static final String PAGE_TITLE = Window.PRICE_BOOKING.text();

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
     * Selects bookings in the TEST1 dropdown (test1Input): opens it and clicks each option, the li whose p shows
     * that text: {@code pricePage.selectBookings("Booking A", "Booking B")}. The list is left open; close it with
     * {@link #closeDropdown()}, or use {@link #applySearchs}.
     *
     * @param bookings the option texts, exactly as shown
     * @return this page
     */
    public PricePage selectBookings(String... bookings) {
        selectFromDropdown("test1Input", bookings);
        return this;
    }

    /**
     * Selects days in the TEST2 dropdown (test2Input), like {@link #selectBookings}:
     * {@code pricePage.selectDays("Monday", "Friday")}.
     *
     * @param days the option texts, exactly as shown
     * @return this page
     */
    public PricePage selectDays(String... days) {
        selectFromDropdown("test2Input", days);
        return this;
    }

    /**
     * Selects bookings and days, closing each list with Escape: {@code pricePage.applySearchs(List.of("Booking A"),
     * List.of("Monday", "Friday"))}. The bookings list is closed before the days dropdown is opened, so it cannot
     * cover the days input, and the days list is closed at the end, so the next step sees the page.
     *
     * @param bookings the booking texts for the TEST1 dropdown
     * @param days     the day texts for the TEST2 dropdown
     * @return this page
     */
    public PricePage applySearchs(List<String> bookings, List<String> days) {
        selectBookings(bookings.toArray(String[]::new));
        closeDropdown();
        selectDays(days.toArray(String[]::new));
        closeDropdown();
        return this;
    }

    /**
     * Closes the open dropdown list with Escape and waits until it is gone.
     *
     * @return this page
     */
    public PricePage closeDropdown() {
        page.keyboard().press("Escape");
        el.get("dropdownList").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
        return this;
    }


    /**
     * Opens a dropdown by clicking its input, then clicks each option. Before every option it checks that the list
     * is open, and opens it again if a click closed it (some dropdowns close after each choice).
     */
    private void selectFromDropdown(String input, String... options) {
        el.get(input).click();
        for (String option : options) {
            if (!el.get("dropdownList").isVisible()) el.get(input).click();
            el.get("dropdownListItem", "OPTION", option).click();   // waits for the list and the option
        }
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
     * Waits for the grid to load, searches a column for a value and ticks the checkbox in a given column of that
     * row: {@code pricePage.checkGridRow("Booking", "Booking A", "Select")}.
     *
     * @param searchHeader   the column to search, by its header text, for example "Booking"
     * @param value          the value to find in it, exactly as shown
     * @param checkboxHeader the column that holds the checkbox, by its header text
     * @return this page
     * @throws AssertionError if the grid does not load or the value is not in the column
     */
    public PricePage checkGridRow(String searchHeader, String value, String checkboxHeader) {
        grid().waitUntilLoaded().element(Map.of(searchHeader, value), "checkbox", checkboxHeader).check();
        return this;
    }

    /**
     * Checks that the Price window is shown and ready: its browser page title is {@link #PAGE_TITLE} and its Search
     * Data button is visible. {@link WindowNavigator} calls it when it opens the Price window, so the next step
     * only runs once the window's controls are there.
     *
     * @return this page
     * @throws AssertionError if the title or the Search Data button is not shown within ui.waits.assertionMs
     */
    @Override
    public PricePage verifyDisplayed() {
        CommonActions.assertTitle(page, PAGE_TITLE);
        CommonActions.assertVisible(el.get("searchDataButton"), "the Price window's Search Data button");
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
