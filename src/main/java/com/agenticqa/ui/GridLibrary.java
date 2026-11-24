package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * The data grid every page has, with the same functions on every page. Columns are named by their header text, so
 * tests never deal with column ids:
 * <pre>
 * GridLibrary grid = pricePage.grid().waitUntilLoaded();
 * grid.cell("Booking", "Booking A");                                  // the Booking cells that show "Booking A"
 * grid.rows(Map.of("Booking", "Booking A", "Status", "Open"));        // every row matching both columns
 * grid.values(Map.of("Booking", "Booking A"), "Status");              // the Status texts of those rows
 * grid.elements(Map.of("Booking", "Booking A"), "checkbox");          // the checkboxes in those rows
 * grid.elements(Map.of("Booking", "Booking A"), "link", "Customer");  // ... in their Customer column
 * grid.columnValues("Booking");                                       // all Booking values shown
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
     * Every row whose columns show all the given values, for example
     * {@code grid.rows(Map.of("Booking", "Booking A", "Status", "Open"))}: every part of each row (the pinned and
     * the scrolling part share its row-index, so the columns may be in different parts).
     *
     * @param match column header text to the value that column must show, exactly as shown
     * @return the matching rows (one or more)
     * @throws AssertionError if no row shows all the values
     */
    public Locator rows(Map<String, String> match) {
        if (match.isEmpty()) {
            throw new IllegalArgumentException("Give at least one column and value to find grid rows");
        }
        Set<String> rowIndexes = null;
        for (Map.Entry<String, String> column : match.entrySet()) {
            Locator cells = cell(column.getKey(), column.getValue());
            CommonActions.assertVisible(cells.first(),
                    "'" + column.getValue() + "' in the grid column '" + column.getKey() + "'");
            @SuppressWarnings("unchecked")
            List<String> found = (List<String>) cells.evaluateAll(
                    "cells => cells.map(c => c.closest('[row-index]').getAttribute('row-index'))");
            if (rowIndexes == null) {
                rowIndexes = new LinkedHashSet<>(found);
            } else {
                rowIndexes.retainAll(found);
            }
        }
        if (rowIndexes.isEmpty()) {
            Assert.fail("No grid row where " + describe(match) + ", on " + page.url());
        }
        StringJoiner anyOfThem = new StringJoiner(", ");
        rowIndexes.forEach(index -> anyOfThem.add(selector("row") + "[row-index='" + index + "']"));
        return page.locator(anyOfThem.toString());
    }

    /**
     * The cells of a column in every row whose columns show all the given values:
     * {@code grid.cells(Map.of("Name", "COE", "Role", "Admin"), "Email")}.
     *
     * @param match        column header text to the value that column must show, as for {@link #rows(Map)}
     * @param targetHeader the column whose cells are wanted
     * @return the cells, one per matching row
     * @throws AssertionError if no row shows all the values
     */
    public Locator cells(Map<String, String> match, String targetHeader) {
        return rows(match).locator(selector("cell") + "[col-id='" + columnId(targetHeader) + "']");
    }

    /**
     * The texts of a column in every row whose columns show all the given values, top to bottom:
     * {@code grid.values(Map.of("Name", "COE"), "Role")}.
     *
     * @param match        column header text to the value that column must show, as for {@link #rows(Map)}
     * @param targetHeader the column whose values are wanted
     * @return the cell texts, trimmed, one per matching row
     * @throws AssertionError if no row shows all the values
     */
    public List<String> values(Map<String, String> match, String targetHeader) {
        return cells(match, targetHeader).allTextContents().stream().map(String::trim).toList();
    }

    /**
     * Every element of a type in every row whose columns show all the given values, wherever it is in the row
     * (also in a pinned column): {@code grid.elements(Map.of("Booking", "Booking A"), "checkbox")}.
     * <p>
     * {@code elementType} is one of the element types in locators/grid.json: checkbox, radio, button, link, input
     * (a text input or textarea), dropdown or image. Anything else is used as a CSS selector inside the rows, so a
     * tag ({@code "span"}), a class ({@code ".ag-icon"}) or an attribute ({@code "[data-testid='edit']"}) works too.
     *
     * @param match       column header text to the value that column must show, as for {@link #rows(Map)}
     * @param elementType the element type or a CSS selector
     * @return the elements; use all() to act on each, first() for one, count() ...
     * @throws AssertionError if no row shows all the values
     */
    public Locator elements(Map<String, String> match, String elementType) {
        return rows(match).locator(elementSelector(elementType));
    }

    /**
     * Every element of a type in a given column of every row whose columns show all the given values:
     * {@code grid.elements(Map.of("Booking", "Booking A"), "link", "Customer")}.
     *
     * @param match        column header text to the value that column must show, as for {@link #rows(Map)}
     * @param elementType  the element type or a CSS selector, as for {@link #elements(Map, String)}
     * @param targetHeader the column that holds the elements, by its header text
     * @return the elements
     * @throws AssertionError if no row shows all the values
     */
    public Locator elements(Map<String, String> match, String elementType, String targetHeader) {
        return cells(match, targetHeader).locator(elementSelector(elementType));
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

    /**
     * The selector for an element type: the grid.json entry of that name (checkbox, button, ...), case ignored, or
     * the text itself as a CSS selector.
     */
    private static String elementSelector(String elementType) {
        String name = elementType.trim().toLowerCase(Locale.ROOT);
        return ELEMENT_TYPES.contains(name) ? selector(name) : elementType.trim();
    }

    /** The grid.json entries that are element types; the others (row, cell, ...) are the grid's structure. */
    private static final Set<String> ELEMENT_TYPES =
            Set.of("checkbox", "radio", "button", "link", "input", "dropdown", "image");

    /** "'Booking' is 'Booking A' and 'Status' is 'Open'", for messages. */
    private static String describe(Map<String, String> match) {
        StringJoiner text = new StringJoiner(" and ");
        match.forEach((header, value) -> text.add("'" + header + "' is '" + value + "'"));
        return text.toString();
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
