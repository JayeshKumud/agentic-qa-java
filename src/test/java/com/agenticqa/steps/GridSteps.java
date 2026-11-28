package com.agenticqa.steps;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.GridLibrary;
import com.agenticqa.ui.WindowNavigator;
import com.microsoft.playwright.Locator;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Steps for the data grid ({@link GridLibrary}). They work on the grid of the window the scenario is on: the home
 * page, or the window the last switch opened. Columns are named by their header text, values exactly as shown.
 * <pre>
 * Then the grid is loaded
 * Then the grid column "Booking" contains "Booking A"
 * Then the grid column "Booking" does not contain "Booking X"
 * Then the grid column "Booking" shows
 *   | Booking A |
 *   | Booking C |
 * </pre>
 * A row is found by one or more columns: end the step with "matching" and give the columns and values as a
 * two-column table (header | value). Every row that shows all of them is used: check, uncheck and the checks
 * work on each of those rows, a click clicks the first:
 * <pre>
 * Then in the grid rows matching, "Email" is "coe@example.com"
 *   | Name | COE   |
 *   | Role | Admin |
 * When I check the grid rows matching
 * When I uncheck the grid rows matching
 * When I check "Select" in the grid rows matching
 * Then the grid rows matching are checked
 * Then the grid rows matching are not checked
 * When I click "Email" in the grid rows matching
 * When I click the "button" in the grid rows matching
 * When I click the "link" in column "Customer" of the grid rows matching
 * </pre>
 * The element type is checkbox, radio, button, link, input, dropdown or image, or a CSS selector such as
 * {@code "[data-testid='edit']"} ({@link GridLibrary#elements(java.util.Map, String)}).
 * <p>
 * On a window with several grids, pick one first by a column only it has; every later grid step on that window,
 * these and the Price grid steps, then works on it:
 * <pre>
 * When I use the grid with column "Booking"
 * When I check the grid rows matching
 *   | Booking | Booking A |
 * When I use the grid with column "Customer"
 * When I use the only grid
 * </pre>
 * Grids that show the same columns are picked by the element they are in, a locator of the page's locator file:
 * <pre>
 * When I use the grid in "bookingsGrid"
 * </pre>
 */
public class GridSteps {

    private static final String CHECKBOX = "checkbox";

    private final WindowNavigator windows;
    private final TestSession session;

    /**
     * Receives the scenario's window navigator and session from PicoContainer.
     *
     * @param windows knows which window the scenario is on
     * @param session the per-scenario session, which keeps the grid chosen per window
     */
    public GridSteps(WindowNavigator windows, TestSession session) {
        this.windows = windows;
        this.session = session;
    }

    /**
     * Picks the grid of the current window that has a column with this header, for windows with several grids:
     * {@code When I use the grid with column "Booking"}. Every later grid step on this window (GridSteps and
     * PriceSteps, and the page methods they call) works on that grid, until another grid is picked; other windows
     * keep their own choice. Waits until that grid has loaded, so a wrong or shared column fails here.
     *
     * @param column the header text of a column only that grid has
     */
    @When("I use the grid with column {string}")
    public void useGridWithColumn(String column) {
        session.useGrid(windows.current().window(), column);
        grid().waitUntilLoaded();
    }

    /**
     * Picks the grid of the current window that is in an element of its page, for windows whose grids no column
     * tells apart (they show the same columns): {@code When I use the grid in "bookingsGrid"}. The name is a
     * locator in the current page's locator file, for example {@code "bookingsGrid": "#bookings"}. Every later grid
     * step on this window works on that grid, as after "I use the grid with column ...". Waits until that grid has
     * loaded, so an unknown name, an empty container or one with several grids fails here.
     *
     * @param containerName the locator name of the element the grid is in
     */
    @When("I use the grid in {string}")
    public void useGridIn(String containerName) {
        windows.current().gridIn(containerName).waitUntilLoaded();   // fails before the choice is kept
        session.useGridIn(windows.current().window(), containerName);
    }

    /** Goes back to the current window's only grid: {@code When I use the only grid}. */
    @When("I use the only grid")
    public void useOnlyGrid() {
        session.useGrid(windows.current().window(), null);
    }

    /** The grid of the current window. */
    private GridLibrary grid() {
        return windows.current().grid();
    }

    /** Waits until the grid has loaded: {@code Then the grid is loaded}. */
    @Then("the grid is loaded")
    public void gridIsLoaded() {
        grid().waitUntilLoaded();
    }

    /**
     * Checks that a column shows a value (waits for it): {@code Then the grid column "Booking" contains "Booking A"}.
     *
     * @param header the column header's text
     * @param value  the value, exactly as shown
     */
    @Then("the grid column {string} contains {string}")
    public void columnContains(String header, String value) {
        assertTrue(grid().contains(header, value), "grid column '" + header + "' shows '" + value + "'");
    }

    /**
     * Checks that a column does not show a value, once the grid has loaded:
     * {@code Then the grid column "Booking" does not contain "Booking X"}.
     *
     * @param header the column header's text
     * @param value  the value, exactly as shown
     */
    @Then("the grid column {string} does not contain {string}")
    public void columnDoesNotContain(String header, String value) {
        GridLibrary grid = grid().waitUntilLoaded();
        assertEquals(grid.cell(header, value).count(), 0,
                "grid column '" + header + "' should not show '" + value + "', but does");
    }

    /**
     * Checks that a column shows each value of a one-column table, in any order:
     * <pre>
     * Then the grid column "Booking" shows
     *   | Booking A |
     *   | Booking C |
     * </pre>
     *
     * @param header the column header's text
     * @param values the values that must be shown
     */
    @Then("the grid column {string} shows")
    public void columnShows(String header, List<String> values) {
        GridLibrary grid = grid().waitUntilLoaded();
        for (String value : values) {
            assertTrue(grid.contains(header, value), "grid column '" + header + "' shows '" + value + "'");
        }
    }

    /**
     * Checks a cell in the row that shows all the table's values:
     * <pre>
     * Then in the grid rows matching, "Email" is "coe@example.com"
     *   | Name | COE   |
     *   | Role | Admin |
     * </pre>
     *
     * @param targetHeader the column to check in that row
     * @param expected     the value it must show (spaces around it ignored; numbers by value: 70 is 70.00)
     * @param match        the table: column header | value, one row per column
     */
    @Then("in the grid rows matching, {string} is {string}")
    public void cellInMatchingRowIs(String targetHeader, String expected, Map<String, String> match) {
        for (String actual : grid().values(match, targetHeader)) {
            assertTrue(GridLibrary.sameValue(actual, expected), "'" + targetHeader + "' in the grid rows matching "
                    + match + " shows '" + actual + "', expected '" + expected + "'");
        }
    }

    /**
     * Ticks the checkbox of the row that shows all the table's values, wherever it is in the row:
     * {@code When I check the grid rows matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I check the grid rows matching")
    public void checkMatchingRow(Map<String, String> match) {
        grid().elements(match, CHECKBOX).all().forEach(Locator::check);
    }

    /**
     * Unticks the checkbox of the row that shows all the table's values:
     * {@code When I uncheck the grid rows matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I uncheck the grid rows matching")
    public void uncheckMatchingRow(Map<String, String> match) {
        List<Locator> checkboxes = grid().elements(match, CHECKBOX).all();
        assertFalse(checkboxes.isEmpty(), "a checkbox in the grid rows matching " + match);
        for (Locator checkbox : checkboxes) {
            checkbox.uncheck();
            assertFalse(checkbox.isChecked(), "checkbox in the grid rows matching " + match + " is unchecked");
        }
    }

    /**
     * Ticks the checkbox in a given column of the row that shows all the table's values:
     * {@code When I check "Select" in the grid rows matching} + table.
     *
     * @param checkboxHeader the column that holds the checkbox
     * @param match          the table: column header | value
     */
    @When("I check {string} in the grid rows matching")
    public void checkInMatchingRow(String checkboxHeader, Map<String, String> match) {
        grid().elements(match, CHECKBOX, checkboxHeader).all().forEach(Locator::check);
    }

    /**
     * Checks that the checkbox of the row that shows all the table's values is ticked:
     * {@code Then the grid rows matching are checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the grid rows matching are checked")
    public void matchingRowIsChecked(Map<String, String> match) {
        for (Locator checkbox : grid().elements(match, CHECKBOX).all()) {
            assertTrue(checkbox.isChecked(), "every checkbox in the grid rows matching " + match + " is checked");
        }
    }

    /**
     * Checks that the checkbox of the row that shows all the table's values is not ticked:
     * {@code Then the grid rows matching are not checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the grid rows matching are not checked")
    public void matchingRowIsNotChecked(Map<String, String> match) {
        for (Locator checkbox : grid().elements(match, CHECKBOX).all()) {
            assertFalse(checkbox.isChecked(), "no checkbox in the grid rows matching " + match + " is checked");
        }
    }

    /**
     * Clicks a cell in the row that shows all the table's values:
     * {@code When I click "Email" in the grid rows matching} + table.
     *
     * @param targetHeader the column whose cell is clicked
     * @param match        the table: column header | value
     */
    @When("I click {string} in the grid rows matching")
    public void clickCellInMatchingRow(String targetHeader, Map<String, String> match) {
        grid().cells(match, targetHeader).first().click();
    }

    /**
     * Clicks an element of a type anywhere in the row that shows all the table's values:
     * {@code When I click the "button" in the grid rows matching} + table.
     *
     * @param elementType checkbox, radio, button, link, input, dropdown, image, or a CSS selector
     * @param match       the table: column header | value
     */
    @When("I click the {string} in the grid rows matching")
    public void clickElementInMatchingRow(String elementType, Map<String, String> match) {
        grid().elements(match, elementType).first().click();
    }

    /**
     * Clicks an element of a type in a given column of the row that shows all the table's values:
     * {@code When I click the "link" in column "Customer" of the grid rows matching} + table.
     *
     * @param elementType  checkbox, radio, button, link, input, dropdown, image, or a CSS selector
     * @param targetHeader the column that holds the element, by its header text
     * @param match        the table: column header | value
     */
    @When("I click the {string} in column {string} of the grid rows matching")
    public void clickElementInColumnOfMatchingRow(String elementType, String targetHeader, Map<String, String> match) {
        grid().elements(match, elementType, targetHeader).first().click();
    }
}
