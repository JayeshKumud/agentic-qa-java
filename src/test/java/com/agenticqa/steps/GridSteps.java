package com.agenticqa.steps;

import com.agenticqa.ui.GridLibrary;
import com.agenticqa.ui.WindowNavigator;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

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
 * Then in the grid row where "Booking" is "Booking A", "Status" is "Open"
 * When I check the grid row where "Booking" is "Booking A"
 * When I check "Select" in the grid row where "Booking" is "Booking A"
 * When I check column 1 in the grid row where "Booking" is "Booking A"
 * Then the grid row where "Booking" is "Booking A" is checked
 * When I click "Status" in the grid row where "Booking" is "Booking A"
 * </pre>
 */
public class GridSteps {

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
     * Checks a cell in the row found by another column's value:
     * {@code Then in the grid row where "Booking" is "Booking A", "Status" is "Open"}.
     *
     * @param header       the column to search
     * @param value        the value that finds the row
     * @param targetHeader the column to check in that row
     * @param expected     the text it must show, exactly (spaces around it ignored)
     */
    @Then("in the grid row where {string} is {string}, {string} is {string}")
    public void cellInRowIs(String header, String value, String targetHeader, String expected) {
        String actual = grid().cell(header, value, targetHeader).textContent().trim();
        assertEquals(actual, expected, "'" + targetHeader + "' in the grid row where '" + header + "' is '" + value + "'");
    }

    /**
     * Ticks the checkbox of the row found by a column's value, wherever it is in the row (also in a pinned
     * selection column): {@code When I check the grid row where "Booking" is "Booking A"}.
     *
     * @param header the column to search
     * @param value  the value that finds the row
     */
    @When("I check the grid row where {string} is {string}")
    public void checkRow(String header, String value) {
        grid().checkbox(header, value).check();
    }

    /**
     * Unticks the checkbox of the row found by a column's value:
     * {@code When I uncheck the grid row where "Booking" is "Booking A"}.
     *
     * @param header the column to search
     * @param value  the value that finds the row
     */
    @When("I uncheck the grid row where {string} is {string}")
    public void uncheckRow(String header, String value) {
        grid().checkbox(header, value).uncheck();
    }

    /**
     * Ticks the checkbox in a given column of the row, for rows with more than one checkbox:
     * {@code When I check "Select" in the grid row where "Booking" is "Booking A"}.
     *
     * @param checkboxHeader the column that holds the checkbox
     * @param header         the column to search
     * @param value          the value that finds the row
     */
    @When("I check {string} in the grid row where {string} is {string}")
    public void checkInRow(String checkboxHeader, String header, String value) {
        grid().checkbox(header, value, checkboxHeader).check();
    }

    /**
     * Ticks the checkbox at a column position of the row, for a checkbox column without a header text:
     * {@code When I check column 1 in the grid row where "Booking" is "Booking A"}.
     *
     * @param columnIndex the position of the checkbox column, counted from 1 at the left as shown on screen
     * @param header      the column to search
     * @param value       the value that finds the row
     */
    @When("I check column {int} in the grid row where {string} is {string}")
    public void checkColumnInRow(int columnIndex, String header, String value) {
        grid().checkbox(header, value, columnIndex).check();
    }

    /**
     * Checks that the row's checkbox is ticked:
     * {@code Then the grid row where "Booking" is "Booking A" is checked}.
     *
     * @param header the column to search
     * @param value  the value that finds the row
     */
    @Then("the grid row where {string} is {string} is checked")
    public void rowIsChecked(String header, String value) {
        assertTrue(grid().checkbox(header, value).isChecked(),
                "the grid row where '" + header + "' is '" + value + "' is checked");
    }

    /**
     * Checks that the row's checkbox is not ticked:
     * {@code Then the grid row where "Booking" is "Booking A" is not checked}.
     *
     * @param header the column to search
     * @param value  the value that finds the row
     */
    @Then("the grid row where {string} is {string} is not checked")
    public void rowIsNotChecked(String header, String value) {
        assertFalse(grid().checkbox(header, value).isChecked(),
                "the grid row where '" + header + "' is '" + value + "' is not checked");
    }

    /**
     * Clicks a cell in the row found by another column's value, for example to open a link in it:
     * {@code When I click "Status" in the grid row where "Booking" is "Booking A"}.
     *
     * @param targetHeader the column whose cell is clicked
     * @param header       the column to search
     * @param value        the value that finds the row
     */
    @When("I click {string} in the grid row where {string} is {string}")
    public void clickCell(String targetHeader, String header, String value) {
        grid().cell(header, value, targetHeader).click();
    }
}
