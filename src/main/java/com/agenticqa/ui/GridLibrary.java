package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.testng.Assert;

import java.math.BigDecimal;
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
 * grid.cellIn(row, "Price");                                          // the Price cell of a row you have
 * grid.columnValues("Booking");                                       // all Booking values shown
 * </pre>
 * The selectors are in {@code locators/grid.json}, written for AG Grid's markup (header cells and cells carry a
 * col-id, rows a row-index). A row is found by its row-index, so a checkbox in a pinned column (a separate part of
 * the grid) is found too.
 * <p>
 * A grid only renders the rows that fit on screen; a value further down is found once it is scrolled into view,
 * for example after filtering the grid.
 * <p>
 * Every lookup searches inside one grid (its {@code root} in grid.json), never the whole page, so a page with two
 * grids cannot mix their rows (both have a row-index 0). {@code pricePage.grid()} is the page's only grid; on a page
 * with several, pick one by a column only it has: {@code pricePage.grid("Booking")}.
 */
public final class GridLibrary {

    private static final String LOCATORS = "grid";

    private final Page page;
    private final PageElements el;
    /** The grid this library works on: every lookup is inside it. */
    private final Locator root;
    /** The column that picks the grid, or null for the window's only grid. */
    private final String pickedBy;

    /**
     * The window's only grid. {@link #waitUntilLoaded()} fails clearly if the window has more than one: use
     * {@link #GridLibrary(Page, Healer, String)} there.
     *
     * @param page   the window the grid is in
     * @param healer this scenario's healer
     */
    public GridLibrary(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
        this.root = el.get("root");
        this.pickedBy = null;
    }

    /**
     * The grid of the window that has a column with this header, for windows with several grids:
     * {@code new GridLibrary(page, healer, "Booking")}, usually {@code pricePage.grid("Booking")}. Pick a column only
     * that grid has; {@link #waitUntilLoaded()} fails clearly if no grid or several grids have it.
     *
     * @param page   the window the grids are in
     * @param healer this scenario's healer
     * @param column the header text of a column of that grid, exactly as shown
     */
    public GridLibrary(Page page, Healer healer, String column) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
        this.root = el.get("root").filter(new Locator.FilterOptions().setHas(el.get("headerCell", "HEADER", column)));
        this.pickedBy = column;
    }

    /**
     * Waits until the grid has loaded: the grid and its header are shown, the loading overlay is gone, and there is
     * at least one row or the "no rows" message. Fails clearly (TestNG) if it does not happen within
     * ui.waits.assertionMs.
     *
     * @return this grid
     */
    public GridLibrary waitUntilLoaded() {
        CommonActions.assertVisible(root.first(), name());
        int grids = root.count();
        if (grids > 1) {
            Assert.fail(pickedBy == null
                    ? grids + " grids on " + page.url() + ": pick one by a column only it has, with the step"
                            + " 'I use the grid with column \"Booking\"' or pricePage.grid(\"Booking\")"
                    : grids + " grids have a '" + pickedBy + "' column on " + page.url()
                            + ": pick the grid by a column only it has");
        }
        CommonActions.assertVisible(el.within(root, "header"), "the header of " + name());
        try {
            el.within(root, "loadingOverlay").waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(Settings.getAssertionMs()));
        } catch (PlaywrightException stillLoading) {
            Assert.fail(name() + " was still loading after " + Settings.getAssertionMs() + " ms, on " + page.url());
        }
        CommonActions.assertVisible(el.within(root, "rowsOrNoRows").first(),
                "a row or the 'no rows' message of " + name());
        return this;
    }

    /**
     * The grid element itself ({@code root} in grid.json, AG Grid's {@code .ag-root-wrapper}), for anything this
     * class has no method for. Searches from it stay inside this grid:
     * <pre>
     * Locator bookingsGrid = pricePage.grid("Booking").root();
     * bookingsGrid.locator("button:has-text('Export')").click();   // the Export button of that grid only
     * </pre>
     * It is the grid the library was created for: the window's only grid, the one chosen with "I use the grid with
     * column ...", or the one with the column given to {@code grid("...")}. Call {@link #waitUntilLoaded()} first
     * if the grid may still be loading, which also fails clearly if the choice finds several grids.
     *
     * @return the grid's root element
     */
    public Locator root() {
        return root;
    }

    /** "the grid", or "the grid with the column 'Booking'", for messages. */
    private String name() {
        return pickedBy == null ? "the grid" : "the grid with the column '" + pickedBy + "'";
    }

    /**
     * The column id of a column, read from its header.
     *
     * @param header the column header's text, exactly as shown, for example "Booking"
     * @return the col-id its cells carry
     * @throws AssertionError if no column header shows that text
     */
    public String columnId(String header) {
        Locator headerCell = root.locator(selector("headerCell").replace("HEADER", header));
        CommonActions.assertVisible(headerCell, "the column '" + header + "' of " + name());
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
        return root.locator(anyOfThem.toString());
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
        return cellIn(rows(match), targetHeader);
    }

    /**
     * The cell of a column in a row you already have, for example one of {@link #rows(Map)}:
     * {@code grid.cellIn(row, "Price")}. Every part of the row is searched (AG Grid's pinned and scrolling parts),
     * so the column may be in either.
     *
     * @param row          the row (or rows: then their cells of that column, one per row)
     * @param targetHeader the column whose cell is wanted, by its header text
     * @return the cell
     */
    public Locator cellIn(Locator row, String targetHeader) {
        return row.locator(selector("cell") + "[col-id='" + columnId(targetHeader) + "']");
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
        return shownValues(root.locator(columnCells(header)));
    }

    /** A number as grids show it: optional sign, digits (thousands may be grouped with commas), optional decimals. */
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d{1,3}(,\\d{3})+|\\d+)?(\\.\\d+)?");

    /**
     * Tells whether a value a cell shows is the expected one: the same text once whitespace is collapsed and
     * trimmed, or, when both are numbers, the same number, so "70" matches "70.00" and "1,234.5" matches "1234.50".
     * {@link #cell}, {@link #contains} and {@link #rows} match values this way too.
     *
     * @param actual   what the cell shows, for example from {@link #values}
     * @param expected the value the test expects
     * @return true if they are the same value
     */
    public static boolean sameValue(String actual, String expected) {
        String a = actual == null ? "" : actual.replaceAll("\\s+", " ").trim();
        String e = expected == null ? "" : expected.replaceAll("\\s+", " ").trim();
        if (a.equals(e)) return true;
        BigDecimal aNumber = number(a);
        BigDecimal eNumber = number(e);
        return aNumber != null && eNumber != null && aNumber.compareTo(eNumber) == 0;
    }

    /** The number a text shows, or null if it is not a number. */
    private static BigDecimal number(String text) {
        if (!NUMBER.matcher(text).matches() || !text.matches(".*\\d.*")) return null;
        return new BigDecimal(text.replace(",", ""));
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
     * or that holds a text input, textarea or dropdown whose value is {@code want}. Values are compared as
     * {@link #sameValue} does: whitespace collapsed and trimmed, and numbers by their value (70 matches 70.00).
     * Fields are as in {@link #FIELDS}.
     */
    private static final String MATCHING_CELLS = "(cells, want) => {" + FIELDS + """
              const norm = s => (s ?? '').replace(/\\s+/g, ' ').trim();
              const NUMBER = /^[+-]?(\\d{1,3}(,\\d{3})+|\\d+)?(\\.\\d+)?$/;
              const number = s => NUMBER.test(s) && /\\d/.test(s) ? Number(s.replace(/,/g, '')) : null;
              const target = norm(want);
              const targetNumber = number(target);
              const same = s => {
                const text = norm(s);
                return text === target || (targetNumber !== null && number(text) === targetNumber);
              };
              return cells
                  .filter(c => same(c.textContent) || fields(c).some(f => same(shown(f))))
                  .map(c => [c.closest('[row-index]').getAttribute('row-index'), c.getAttribute('col-id')]);
            }""";

    /** The CSS selector of a column's cells in every row. */
    private String columnCells(String header) {
        return selector("row") + " " + selector("cell") + "[col-id='" + columnId(header) + "']";
    }

    /** The [row-index, col-id] of each cell matching {@code cellSelector} that shows the value, right now. */
    @SuppressWarnings("unchecked")
    private List<List<String>> matchingCells(String cellSelector, String value) {
        return (List<List<String>>) root.locator(cellSelector).evaluateAll(MATCHING_CELLS, value);
    }

    /**
     * Waits up to ui.waits.assertionMs for a cell of this grid matching {@code cellSelector} to show the value,
     * checking every 100 ms.
     */
    private boolean waitForMatch(String cellSelector, String value) {
        long end = System.currentTimeMillis() + Settings.getAssertionMs();
        while (true) {
            if (!matchingCells(cellSelector, value).isEmpty()) return true;
            if (System.currentTimeMillis() >= end) return false;
            page.waitForTimeout(100);
        }
    }

    /** A locator for the cells at the given [row-index, col-id] places; it matches nothing if there are none. */
    private Locator cellsAt(List<List<String>> places) {
        if (places.isEmpty()) {
            return root.locator(selector("cell") + ":not(*)");
        }
        StringJoiner anyOfThem = new StringJoiner(", ");
        places.forEach(place -> anyOfThem.add(selector("row") + "[row-index='" + place.get(0) + "'] "
                + selector("cell") + "[col-id='" + place.get(1) + "']"));
        return root.locator(anyOfThem.toString());
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
