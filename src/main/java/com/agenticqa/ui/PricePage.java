package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** The grid column that shows ranges such as "10->20" (see {@link #verifySelectionShown}). */
    public static final String SPLIT_HEADER = "Splited";

    /** What separates the two sides of a {@value #SPLIT_HEADER} range, as in "10->20". */
    public static final String RANGE = "->";

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
     * Waits for the grid to load, searches a column for a value and ticks the checkbox in a given column of every
     * row that shows it: {@code pricePage.checkGridRow("Booking", "Booking A", "Select")}.
     *
     * @param searchHeader   the column to search, by its header text, for example "Booking"
     * @param value          the value to find in it, exactly as shown
     * @param checkboxHeader the column that holds the checkbox, by its header text
     * @return this page
     * @throws AssertionError if the grid does not load or the value is not in the column
     */
    public PricePage checkGridRow(String searchHeader, String value, String checkboxHeader) {
        grid().waitUntilLoaded().elements(Map.of(searchHeader, value), "checkbox", checkboxHeader).all()
                .forEach(Locator::check);
        return this;
    }

    /**
     * Checks that the cell of column {@code targetHeader} in every Price grid row matching {@code match} holds the
     * div of a selection: for TEST1 the cell's div whose class contains model1, for TEST2 the one whose class
     * contains model2 (locators test1Div and test2Div in pricepage.json):
     * {@code pricePage.verifySelectionShown(Map.of("Booking", "Booking A", "Splited", "10"), "TEST1", "Price")}.
     * <p>
     * The {@value #SPLIT_HEADER} column shows ranges such as "10->20": a single value ("10" or "20") matches a row
     * if either side is that value, and a range ("10->20") matches only that range. Other columns match as usual.
     *
     * @param match        column header text to the value that column must show
     * @param selection    TEST1 or TEST2 (case ignored)
     * @param targetHeader the column whose cells must hold the div, for example "Price"
     * @return this page
     * @throws AssertionError           if no row matches, or a matching cell does not hold the div
     * @throws IllegalArgumentException if the selection is not TEST1 or TEST2
     */
    public PricePage verifySelectionShown(Map<String, String> match, String selection, String targetHeader) {
        String divLocator = switch (selection.trim().toUpperCase(Locale.ROOT)) {
            case "TEST1" -> "test1Div";
            case "TEST2" -> "test2Div";
            default -> throw new IllegalArgumentException(
                    "Selection must be TEST1 or TEST2, not '" + selection + "'");
        };
        GridLibrary grid = grid().waitUntilLoaded();
        String split = match.get(SPLIT_HEADER);
        List<Locator> cells = new ArrayList<>();
        if (split == null || split.contains(RANGE)) {
            // no Splited value, or a whole range ("10->20"): the usual match
            cells.addAll(grid.cells(match, targetHeader).all());
        } else {
            // one side ("10"): every Splited range shown, in the rows the other columns match, with that side
            Map<String, String> others = new LinkedHashMap<>(match);
            others.remove(SPLIT_HEADER);
            List<String> shown = others.isEmpty() ? grid.columnValues(SPLIT_HEADER) : grid.values(others, SPLIT_HEADER);
            List<String> ranges = shown.stream().distinct().filter(range -> hasSide(range, split)).toList();
            if (ranges.isEmpty()) {
                Assert.fail("No Price grid row whose '" + SPLIT_HEADER + "' range has the side '" + split
                        + "', among the rows matching " + match + " (shown: " + shown + ")");
            }
            for (String range : ranges) {
                Map<String, String> exact = new LinkedHashMap<>(others);
                exact.put(SPLIT_HEADER, range);
                cells.addAll(grid.cells(exact, targetHeader).all());
            }
        }
        Assert.assertFalse(cells.isEmpty(),
                "a '" + targetHeader + "' cell in the Price grid rows matching " + match);
        for (Locator cell : cells) {
            CommonActions.assertVisible(el.within(cell, divLocator),
                    "the " + selection + " div in the '" + targetHeader + "' cell of the Price grid rows matching "
                            + match);
        }
        return this;
    }

    /**
     * Tells whether a {@value #SPLIT_HEADER} value is the given value or a range with it on either side: "10->20"
     * has the sides 10 and 20 (numbers by value, so "10.00" counts as 10).
     */
    private static boolean hasSide(String shown, String side) {
        return Arrays.stream(shown.split(Pattern.quote(RANGE)))
                .anyMatch(part -> GridLibrary.sameValue(part, side));
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
