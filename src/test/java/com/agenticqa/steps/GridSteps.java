package com.agenticqa.steps;

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
 * Then in the grid row matching, "Email" is "coe@example.com"
 *   | Name | COE   |
 *   | Role | Admin |
 * When I check the grid row matching
 * When I uncheck the grid row matching
 * When I check "Select" in the grid row matching
 * Then the grid row matching is checked
 * Then the grid row matching is not checked
 * When I click "Email" in the grid row matching
 * When I click the "button" in the grid row matching
 * When I click the "link" in column "Customer" of the grid row matching
 * </pre>
 * The element type is checkbox, radio, button, link, input, dropdown or image, or a CSS selector such as
 * {@code "[data-testid='edit']"} ({@link GridLibrary#elements(java.util.Map, String)}).
 */
public class GridSteps {

    private static final String CHECKBOX = "checkbox";

    private final WindowNavigator windows;

    /**
     * Receives the scenario's window navigator from PicoContainer.
     *
     * @param windows knows which window the scenario is on
     */
    public GridSteps(WindowNavigator windows) {
        this.windows = windows;
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
     * Then in the grid row matching, "Email" is "coe@example.com"
     *   | Name | COE   |
     *   | Role | Admin |
     * </pre>
     *
     * @param targetHeader the column to check in that row
     * @param expected     the text it must show, exactly (spaces around it ignored)
     * @param match        the table: column header | value, one row per column
     */
    @Then("in the grid row matching, {string} is {string}")
    public void cellInMatchingRowIs(String targetHeader, String expected, Map<String, String> match) {
        for (String actual : grid().values(match, targetHeader)) {
            assertEquals(actual, expected, "'" + targetHeader + "' in the grid rows matching " + match);
        }
    }

    /**
     * Ticks the checkbox of the row that shows all the table's values, wherever it is in the row:
     * {@code When I check the grid row matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I check the grid row matching")
    public void checkMatchingRow(Map<String, String> match) {
        grid().elements(match, CHECKBOX).all().forEach(Locator::check);
    }

    /**
     * Unticks the checkbox of the row that shows all the table's values:
     * {@code When I uncheck the grid row matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I uncheck the grid row matching")
    public void uncheckMatchingRow(Map<String, String> match) {
        grid().elements(match, CHECKBOX).all().forEach(Locator::uncheck);
    }

    /**
     * Ticks the checkbox in a given column of the row that shows all the table's values:
     * {@code When I check "Select" in the grid row matching} + table.
     *
     * @param checkboxHeader the column that holds the checkbox
     * @param match          the table: column header | value
     */
    @When("I check {string} in the grid row matching")
    public void checkInMatchingRow(String checkboxHeader, Map<String, String> match) {
        grid().elements(match, CHECKBOX, checkboxHeader).all().forEach(Locator::check);
    }

    /**
     * Checks that the checkbox of the row that shows all the table's values is ticked:
     * {@code Then the grid row matching is checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the grid row matching is checked")
    public void matchingRowIsChecked(Map<String, String> match) {
        for (Locator checkbox : grid().elements(match, CHECKBOX).all()) {
            assertTrue(checkbox.isChecked(), "every checkbox in the grid rows matching " + match + " is checked");
        }
    }

    /**
     * Checks that the checkbox of the row that shows all the table's values is not ticked:
     * {@code Then the grid row matching is not checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the grid row matching is not checked")
    public void matchingRowIsNotChecked(Map<String, String> match) {
        for (Locator checkbox : grid().elements(match, CHECKBOX).all()) {
            assertFalse(checkbox.isChecked(), "no checkbox in the grid rows matching " + match + " is checked");
        }
    }

    /**
     * Clicks a cell in the row that shows all the table's values:
     * {@code When I click "Email" in the grid row matching} + table.
     *
     * @param targetHeader the column whose cell is clicked
     * @param match        the table: column header | value
     */
    @When("I click {string} in the grid row matching")
    public void clickCellInMatchingRow(String targetHeader, Map<String, String> match) {
        grid().cells(match, targetHeader).first().click();
    }

    /**
     * Clicks an element of a type anywhere in the row that shows all the table's values:
     * {@code When I click the "button" in the grid row matching} + table.
     *
     * @param elementType checkbox, radio, button, link, input, dropdown, image, or a CSS selector
     * @param match       the table: column header | value
     */
    @When("I click the {string} in the grid row matching")
    public void clickElementInMatchingRow(String elementType, Map<String, String> match) {
        grid().elements(match, elementType).first().click();
    }

    /**
     * Clicks an element of a type in a given column of the row that shows all the table's values:
     * {@code When I click the "link" in column "Customer" of the grid row matching} + table.
     *
     * @param elementType  checkbox, radio, button, link, input, dropdown, image, or a CSS selector
     * @param targetHeader the column that holds the element, by its header text
     * @param match        the table: column header | value
     */
    @When("I click the {string} in column {string} of the grid row matching")
    public void clickElementInColumnOfMatchingRow(String elementType, String targetHeader, Map<String, String> match) {
        grid().elements(match, elementType, targetHeader).first().click();
    }
}
