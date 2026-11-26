package com.agenticqa.steps;

import com.agenticqa.ui.GridLibrary;
import com.agenticqa.ui.Pages;
import com.microsoft.playwright.Locator;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Steps for the grid of the Price window, always that window's grid ({@code pages.price().grid()}), whichever
 * window the scenario is on. Rows are found by a two-column table (header | value); every row that shows all the
 * values is used:
 * <pre>
 * Then the Price grid is loaded
 * When I check the Price grid rows matching
 *   | Booking | Booking A |
 *   | Status  | Open      |
 * When I uncheck the Price grid rows matching
 * Then the Price grid rows matching are checked
 * Then the Price grid rows matching are not checked
 * Then in the Price grid rows matching, "Status" is "Open"
 * Then in the Price grid rows matching, "Range" contains "TESTRANGE"
 * When I check "Select" in the Price grid rows with a "PRICE1" Price div for the units
 *   | TEST1 |
 *   | TEST2 |
 * Then the "Price" cells of the Price grid rows matching show the "TEST1" selection
 * When I click the "link" in column "Customer" of the Price grid rows matching
 * </pre>
 * For the grid of whichever window the scenario is on, use {@link GridSteps}.
 */
public class PriceSteps {

    private static final String CHECKBOX = "checkbox";

    private final Pages pages;

    /**
     * Receives the scenario's page objects from PicoContainer.
     *
     * @param pages the scenario's page objects
     */
    public PriceSteps(Pages pages) {
        this.pages = pages;
    }

    /** The grid of the Price window. */
    private GridLibrary grid() {
        return pages.price().grid();
    }

    /** Waits until the Price grid has loaded: {@code Then the Price grid is loaded}. */
    @Then("the Price grid is loaded")
    public void gridIsLoaded() {
        grid().waitUntilLoaded();
    }

    /**
     * Ticks the checkboxes of the Price grid rows that show all the table's values:
     * {@code When I check the Price grid rows matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I check the Price grid rows matching")
    public void checkMatchingRows(Map<String, String> match) {
        List<Locator> checkboxes = grid().waitUntilLoaded().elements(match, CHECKBOX).all();
        assertFalse(checkboxes.isEmpty(), "a checkbox in the Price grid rows matching " + match);
        for (Locator checkbox : checkboxes) {
            checkbox.check();
            assertTrue(checkbox.isChecked(), "checkbox in the Price grid rows matching " + match + " is checked");
        }
    }

    /**
     * Unticks the checkboxes of the Price grid rows that show all the table's values:
     * {@code When I uncheck the Price grid rows matching} + table.
     *
     * @param match the table: column header | value
     */
    @When("I uncheck the Price grid rows matching")
    public void uncheckMatchingRows(Map<String, String> match) {
        List<Locator> checkboxes = grid().waitUntilLoaded().elements(match, CHECKBOX).all();
        assertFalse(checkboxes.isEmpty(), "a checkbox in the Price grid rows matching " + match);
        for (Locator checkbox : checkboxes) {
            checkbox.uncheck();
            assertFalse(checkbox.isChecked(), "checkbox in the Price grid rows matching " + match + " is unchecked");
        }
    }

    /**
     * Checks that every checkbox of the matching Price grid rows is ticked:
     * {@code Then the Price grid rows matching are checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the Price grid rows matching are checked")
    public void matchingRowsAreChecked(Map<String, String> match) {
        List<Locator> checkboxes = grid().waitUntilLoaded().elements(match, CHECKBOX).all();
        assertFalse(checkboxes.isEmpty(), "a checkbox in the Price grid rows matching " + match);
        for (Locator checkbox : checkboxes) {
            assertTrue(checkbox.isChecked(), "checkbox in the Price grid rows matching " + match + " is checked");
        }
    }

    /**
     * Checks that no checkbox of the matching Price grid rows is ticked:
     * {@code Then the Price grid rows matching are not checked} + table.
     *
     * @param match the table: column header | value
     */
    @Then("the Price grid rows matching are not checked")
    public void matchingRowsAreNotChecked(Map<String, String> match) {
        List<Locator> checkboxes = grid().waitUntilLoaded().elements(match, CHECKBOX).all();
        assertFalse(checkboxes.isEmpty(), "a checkbox in the Price grid rows matching " + match);
        for (Locator checkbox : checkboxes) {
            assertFalse(checkbox.isChecked(), "checkbox in the Price grid rows matching " + match + " is not checked");
        }
    }

    /**
     * Checks a column in every Price grid row that shows all the table's values:
     * <pre>
     * Then in the Price grid rows matching, "Status" is "Open"
     *   | Booking | Booking A |
     * </pre>
     *
     * @param targetHeader the column to check in those rows
     * @param expected     the value it must show (spaces around it ignored; numbers by value: 70 is 70.00)
     * @param match        the table: column header | value
     */
    @Then("in the Price grid rows matching, {string} is {string}")
    public void valueInMatchingRowsIs(String targetHeader, String expected, Map<String, String> match) {
        List<String> values = grid().waitUntilLoaded().values(match, targetHeader);
        assertFalse(values.isEmpty(), "a '" + targetHeader + "' cell in the Price grid rows matching " + match);
        for (String actual : values) {
            assertTrue(GridLibrary.sameValue(actual, expected), "'" + targetHeader + "' in the Price grid rows matching "
                    + match + " shows '" + actual + "', expected '" + expected + "'");
        }
    }

    /**
     * Ticks the checkbox in a column of the Price grid rows of the table's units whose Price cell holds a div whose
     * class contains a text ({@link com.agenticqa.ui.PricePage#checkboxesWithPriceDiv}); a unit without such a row is
     * skipped:
     * <pre>
     * When I check "Select" in the Price grid rows with a "PRICE1" Price div for the units
     *   | TEST1 |
     *   | TEST2 |
     * </pre>
     *
     * @param checkboxHeader the column that holds the checkbox, by its header text
     * @param classPart      the text the div's class must contain
     * @param units          the table: one Unit value per line
     */
    @When("I check {string} in the Price grid rows with a {string} Price div for the units")
    public void checkRowsWithPriceDiv(String checkboxHeader, String classPart, List<String> units) {
        for (Locator checkbox : pages.price().checkboxesWithPriceDiv(units, classPart, checkboxHeader)) {
            checkbox.check();
            assertTrue(checkbox.isChecked(), "'" + checkboxHeader + "' checkbox of a Price grid row of the units "
                    + units + " with a '" + classPart + "' Price div is checked");
        }
    }

    /**
     * Checks that a column of the matching Price grid rows contains a text, in at least one of them
     * ({@link com.agenticqa.ui.PricePage#cellContaining}): the text may be anywhere in the cell, case-sensitive.
     * <pre>
     * Then in the Price grid rows matching, "Range" contains "TESTRANGE"
     *   | Booking | Booking A |
     * </pre>
     *
     * @param header the column to look in
     * @param text   the text a cell of that column must contain
     * @param match  the table: column header | value
     */
    @Then("in the Price grid rows matching, {string} contains {string}")
    public void columnContainsInMatchingRows(String header, String text, Map<String, String> match) {
        pages.price().cellContaining(match, header, text);
    }

    /**
     * Checks that a column of every matching Price grid row holds the div of a selection, TEST1 or TEST2
     * ({@link com.agenticqa.ui.PricePage#verifySelectionShown}). The Splited column shows ranges such as "10->20":
     * "10" or "20" matches either side, "10->20" only that range.
     * <pre>
     * Then the "Price" cells of the Price grid rows matching show the "TEST1" selection
     *   | Booking | Booking A |
     *   | Splited | 10        |
     * </pre>
     *
     * @param targetHeader the column whose cells must hold the div
     * @param selection    TEST1 or TEST2
     * @param match        the table: column header | value
     */
    @Then("the {string} cells of the Price grid rows matching show the {string} selection")
    public void selectionShown(String targetHeader, String selection, Map<String, String> match) {
        pages.price().verifySelectionShown(match, selection, targetHeader);
    }

    /**
     * Clicks an element of a type in a given column of the first matching Price grid row:
     * {@code When I click the "link" in column "Customer" of the Price grid rows matching} + table.
     *
     * @param elementType  checkbox, radio, button, link, input, dropdown, image, or a CSS selector
     * @param targetHeader the column that holds the element, by its header text
     * @param match        the table: column header | value
     */
    @When("I click the {string} in column {string} of the Price grid rows matching")
    public void clickElementInColumn(String elementType, String targetHeader, Map<String, String> match) {
        Locator found = grid().waitUntilLoaded().elements(match, elementType, targetHeader);
        assertTrue(found.count() > 0,
                "a '" + elementType + "' in column '" + targetHeader + "' of the Price grid rows matching " + match);
        found.first().click();
    }
}
