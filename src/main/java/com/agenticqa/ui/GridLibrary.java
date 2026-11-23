package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The data grid every page has, with the same functions on every page. Columns are named by their header text, so
 * tests never deal with column ids:
 * <pre>
 * GridLibrary grid = pricePage.grid().waitUntilLoaded();
 * grid.cell("Booking", "Booking A");                        // the Booking cell that shows "Booking A"
 * grid.cell("Booking", "Booking A", "Status").textContent(); // the Status cell in that row
 * grid.checkbox("Booking", "Booking A").check();            // the checkbox in that row
 * grid.checkbox("Booking", "Booking A", "Select").check();  // the checkbox in that row's Select column
 * grid.columnValues("Booking");                             // all Booking values shown
 * </pre>
 * The selectors are in {@code locators/grid.json}, written for AG Grid's markup (header cells and cells carry a
 * col-id, rows a row-index). A row is found by its row-index, so a checkbox in a pinned column (a separate part of
 * the grid) is found too.
 * <p>
 * A grid only renders the rows that fit on screen; a value further down is found once it is scrolled into view,
 * for example after filtering the grid.
 */
public final class GridLibrary {

    private static final String LOCATORS = "grid";

    private final Page page;
    private final PageElements el;

    /**
     * Binds the grid locators to a window.
     *
     * @param page   the window the grid is in
     * @param healer this scenario's healer
     */
    public GridLibrary(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /**
     * Waits until the grid has loaded: the grid and its header are shown, the loading overlay is gone, and there is
     * at least one row or the "no rows" message. Fails clearly (TestNG) if it does not happen within
     * ui.waits.assertionMs.
     *
     * @return this grid
     */
    public GridLibrary waitUntilLoaded() {
        CommonActions.assertVisible(el.get("root"), "the grid");
        CommonActions.assertVisible(el.get("header"), "the grid's header");
        try {
            el.get("loadingOverlay").waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(Settings.getAssertionMs()));
        } catch (PlaywrightException stillLoading) {
            Assert.fail("The grid was still loading after " + Settings.getAssertionMs() + " ms, on " + page.url());
        }
        CommonActions.assertVisible(el.get("rowsOrNoRows").first(), "a grid row or the grid's 'no rows' message");
        return this;
    }

    /**
     * The column id of a column, read from its header.
     *
     * @param header the column header's text, exactly as shown, for example "Booking"
     * @return the col-id its cells carry
     * @throws AssertionError if no column header shows that text
     */
    public String columnId(String header) {
        Locator headerCell = el.get("headerCell", "HEADER", header);
        CommonActions.assertVisible(headerCell, "the grid column '" + header + "'");
        return headerCell.getAttribute("col-id");
    }

    /**
     * Searches a column for a value: the cell in that column whose whole text is {@code value}.
     *
     * @param header the column header's text, for example "Booking"
     * @param value  the cell's text, exactly as shown (spaces around it are ignored)
     * @return the cell (a Locator: call isVisible(), textContent(), click() ...)
     */
    public Locator cell(String header, String value) {
        return page.locator(selector("row") + " " + selector("cell") + "[col-id='" + columnId(header) + "']")
                .filter(new Locator.FilterOptions().setHasText(exactly(value)));
    }

    /**
     * Searches a column for a value and returns the cell of another column in the same row:
     * {@code grid.cell("Booking", "Booking A", "Status")}.
     *
     * @param header       the column to search, for example "Booking"
     * @param value        the value to find in it, exactly as shown
     * @param targetHeader the column whose cell in that row is wanted, for example "Status"
     * @return the cell
     * @throws AssertionError if the value is not in the column
     */
    public Locator cell(String header, String value, String targetHeader) {
        return row(header, value).locator(selector("cell") + "[col-id='" + columnId(targetHeader) + "']");
    }

    /**
     * Searches the whole grid for a value: every cell, in any column, whose whole text is {@code value}.
     *
     * @param value the cell's text, exactly as shown
     * @return the matching cells (may be several)
     */
    public Locator cellWithValue(String value) {
        return page.locator(selector("row") + " " + selector("cell"))
                .filter(new Locator.FilterOptions().setHasText(exactly(value)));
    }

    /**
     * Tells whether a column shows a value, waiting up to ui.waits.assertionMs for it.
     *
     * @param header the column header's text
     * @param value  the value, exactly as shown
     * @return true if a cell of that column shows the value
     */
    public boolean contains(String header, String value) {
        try {
            cell(header, value).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(Settings.getAssertionMs()));
            return true;
        } catch (PlaywrightException notThere) {
            return false;
        }
    }

    /**
     * The row whose column {@code header} shows {@code value}: every part of it (the pinned and the scrolling
     * part share its row-index).
     *
     * @param header the column to search
     * @param value  the value to find, exactly as shown
     * @return the row
     * @throws AssertionError if the value is not in the column
     */
    public Locator row(String header, String value) {
        Locator found = cell(header, value).first();
        CommonActions.assertVisible(found, "'" + value + "' in the grid column '" + header + "'");
        String rowIndex = found.locator("xpath=ancestor::*[@row-index][1]").getAttribute("row-index");
        return page.locator(selector("row") + "[row-index='" + rowIndex + "']");
    }

    /**
     * The checkbox in the row whose column {@code header} shows {@code value}, wherever it is in the row (also in a
     * pinned selection column): {@code grid.checkbox("Booking", "Booking A").check()}.
     *
     * @param header the column to search
     * @param value  the value to find, exactly as shown
     * @return the checkbox; call check(), uncheck(), isChecked()
     * @throws AssertionError if the value is not in the column
     */
    public Locator checkbox(String header, String value) {
        return row(header, value).locator(selector("checkbox")).first();
    }

    /**
     * The checkbox in a given column of the row whose column {@code header} shows {@code value}, for rows with more
     * than one checkbox: {@code grid.checkbox("Booking", "Booking A", "Select").check()}.
     *
     * @param header         the column to search
     * @param value          the value to find, exactly as shown
     * @param checkboxHeader the column that holds the checkbox
     * @return the checkbox
     * @throws AssertionError if the value is not in the column
     */
    public Locator checkbox(String header, String value, String checkboxHeader) {
        return cell(header, value, checkboxHeader).locator(selector("checkbox"));
    }

    /**
     * The values a column shows, top to bottom (only the rows the grid has rendered).
     *
     * @param header the column header's text
     * @return the cell texts, trimmed
     */
    public List<String> columnValues(String header) {
        String id = columnId(header);
        return page.locator(selector("row") + " " + selector("cell") + "[col-id='" + id + "']").allTextContents()
                .stream().map(String::trim).toList();
    }

    /** The selector of a grid locator, for building row and cell lookups. */
    private static String selector(String name) {
        return LocatorRepository.get(LOCATORS, name).selector();
    }

    /**
     * A pattern for a cell whose whole text is the value, spaces around it ignored. Playwright runs it as a
     * JavaScript regex in the browser, so special characters are escaped one by one (Java's Pattern.quote uses
     * \Q...\E, which JavaScript does not know).
     */
    private static Pattern exactly(String value) {
        String escaped = value.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
        return Pattern.compile("^\\s*" + escaped + "\\s*$");
    }
}
