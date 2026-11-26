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
     * Searches a column for a value: the cells in that column whose whole text is {@code value}, or that hold a text
     * input, textarea or dropdown showing {@code value} (an input's value is not part of the cell's text, so a text
     * match alone misses editable cells). It looks once, without waiting; use {@link #contains} to wait for a value.
     *
     * @param header the column header's text, for example "Booking"
     * @param value  the cell's text or input value, exactly as shown (spaces around it are ignored)
     * @return the cells (a Locator: call isVisible(), textContent(), click() ...); count() is 0 if none
     */
    public Locator cell(String header, String value) {
        return cellsAt(matchingCells(columnCells(header), value));
    }

    /**
     * Searches the whole grid for a value: every cell, in any column, whose whole text or input value is
     * {@code value}. It looks once, without waiting.
     *
     * @param value the cell's text or input value, exactly as shown
     * @return the matching cells (may be several, or none)
     */
    public Locator cellWithValue(String value) {
        return cellsAt(matchingCells(selector("row") + " " + selector("cell"), value));
    }

    /**
     * Tells whether a column shows a value (as text or in an input), waiting up to ui.waits.assertionMs for it.
     *
     * @param header the column header's text
     * @param value  the value, exactly as shown
     * @return true if a cell of that column shows the value
     */
    public boolean contains(String header, String value) {
        return waitForMatch(columnCells(header), value);
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
            String cells = columnCells(column.getKey());
            if (!waitForMatch(cells, column.getValue())) {
                Assert.fail("'" + column.getValue() + "' was not shown in the grid column '" + column.getKey()
                        + "' within " + Settings.getAssertionMs() + " ms, on " + page.url());
            }
            List<String> found = matchingCells(cells, column.getValue()).stream().map(place -> place.get(0)).toList();
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
     * @return what each cell shows, trimmed, one per matching row: its text, or the value of the text input,
     *         textarea or dropdown it holds
     * @throws AssertionError if no row shows all the values
     */
    public List<String> values(Map<String, String> match, String targetHeader) {
        return shownValues(cells(match, targetHeader));
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
     * @return what each cell shows, trimmed: its text, or the value of the text input, textarea or dropdown it
     *         holds
     */
    public List<String> columnValues(String header) {
        return shownValues(page.locator(columnCells(header)));
    }

    /**
     * JavaScript helpers shared by the scripts below: {@code fields(cell)} are the text inputs, textareas and
     * dropdowns in a cell (checkboxes, radios and hidden inputs are left out: their value is not what is shown), and
     * {@code shown(field)} is what a field shows (a dropdown: its selected option's text).
     */
    private static final String FIELDS = """
            const shown = el => el.tagName === 'SELECT'
                ? [...el.selectedOptions].map(o => o.textContent).join(' ')
                : el.value;
            const fields = c => [...c.querySelectorAll(
                "input:not([type=checkbox]):not([type=radio]):not([type=hidden]), textarea, select")];
            """;

    /**
     * A JavaScript function (cells) that returns what each cell shows: the values of the fields it holds (joined by
     * a space if several), or its text if it holds none.
     */
    private static final String CELL_VALUES = "cells => {" + FIELDS + """
              return cells.map(c => {
                const fs = fields(c);
                return fs.length ? fs.map(shown).join(' ') : (c.textContent ?? '');
              });
            }""";

    /** What each cell of a locator shows, trimmed, in page order: its fields' values, or its text. */
    @SuppressWarnings("unchecked")
    private static List<String> shownValues(Locator cells) {
        return ((List<String>) cells.evaluateAll(CELL_VALUES)).stream().map(String::trim).toList();
    }

    /**
     * A JavaScript function (cells, want) that returns [row-index, col-id] of each cell whose text is {@code want},
     * or that holds a text input, textarea or dropdown whose value is {@code want}. Whitespace is collapsed and
     * trimmed on both sides. Fields are as in {@link #FIELDS}.
     */
    private static final String MATCHING_CELLS = "(cells, want) => {" + FIELDS + """
              const norm = s => (s ?? '').replace(/\\s+/g, ' ').trim();
              const target = norm(want);
              return cells
                  .filter(c => norm(c.textContent) === target || fields(c).some(f => norm(shown(f)) === target))
                  .map(c => [c.closest('[row-index]').getAttribute('row-index'), c.getAttribute('col-id')]);
            }""";

    /** The CSS selector of a column's cells in every row. */
    private String columnCells(String header) {
        return selector("row") + " " + selector("cell") + "[col-id='" + columnId(header) + "']";
    }

    /** The [row-index, col-id] of each cell matching {@code cellSelector} that shows the value, right now. */
    @SuppressWarnings("unchecked")
    private List<List<String>> matchingCells(String cellSelector, String value) {
        return (List<List<String>>) page.locator(cellSelector).evaluateAll(MATCHING_CELLS, value);
    }

    /** Waits up to ui.waits.assertionMs for a cell matching {@code cellSelector} to show the value. */
    private boolean waitForMatch(String cellSelector, String value) {
        try {
            page.waitForFunction("([selector, want]) => (" + MATCHING_CELLS
                            + ")([...document.querySelectorAll(selector)], want).length > 0",
                    List.of(cellSelector, value),
                    new Page.WaitForFunctionOptions().setTimeout(Settings.getAssertionMs()));
            return true;
        } catch (PlaywrightException notThere) {
            return false;
        }
    }

    /** A locator for the cells at the given [row-index, col-id] places; it matches nothing if there are none. */
    private Locator cellsAt(List<List<String>> places) {
        if (places.isEmpty()) {
            return page.locator(selector("cell") + ":not(*)");
        }
        StringJoiner anyOfThem = new StringJoiner(", ");
        places.forEach(place -> anyOfThem.add(selector("row") + "[row-index='" + place.get(0) + "'] "
                + selector("cell") + "[col-id='" + place.get(1) + "']"));
        return page.locator(anyOfThem.toString());
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
}
