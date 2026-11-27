package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * GridLibrary on a page with two visible grids (AG Grid markup, both with a row-index 0): each grid is found by a
 * column only it has, and its lookups never reach into the other grid. No application needed.
 */
public class GridLibraryTwoGridsTest {

    private static final String PAGE = """
            <html><body>
              %s
              %s
            </body></html>""".formatted(
            grid("Booking", List.of("Booking A", "Booking B"), "Status", List.of("Open", "Closed")),
            grid("Customer", List.of("Ada", "Booking A"), "Status", List.of("Closed", "Open")));

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private Healer healer;

    @BeforeClass
    public void openPage() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        page.setContent(PAGE);
        healer = new Healer();
    }

    @AfterClass(alwaysRun = true)
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void gridWithoutColumnFailsClearlyWhenThePageHasTwo() {
        AssertionError error = expectThrows(AssertionError.class, () -> new GridLibrary(page, healer).waitUntilLoaded());
        assertTrue(error.getMessage().contains("2 grids on"), error.getMessage());
    }

    @Test
    public void eachGridIsFoundByItsOwnColumn() {
        GridLibrary bookings = new GridLibrary(page, healer, "Booking").waitUntilLoaded();
        GridLibrary customers = new GridLibrary(page, healer, "Customer").waitUntilLoaded();

        assertEquals(bookings.columnValues("Status"), List.of("Open", "Closed"));
        assertEquals(customers.columnValues("Status"), List.of("Closed", "Open"));
        assertEquals(bookings.values(Map.of("Booking", "Booking A"), "Status"), List.of("Open"));
        assertEquals(customers.values(Map.of("Customer", "Ada"), "Status"), List.of("Closed"));
    }

    @Test
    public void rowsOfOneGridNeverIncludeTheOtherGridsRowWithTheSameIndex() {
        GridLibrary bookings = new GridLibrary(page, healer, "Booking").waitUntilLoaded();
        // row-index 0 exists in both grids; only the Booking grid's row may come back
        assertEquals(bookings.rows(Map.of("Booking", "Booking A")).count(), 1);
        assertEquals(bookings.cellWithValue("Booking A").count(), 1);
        assertEquals(bookings.elements(Map.of("Status", "Open"), "checkbox").count(), 1);
    }

    @Test
    public void rootIsThatGridOnly() {
        Locator bookings = new GridLibrary(page, healer, "Booking").waitUntilLoaded().root();
        assertEquals(bookings.count(), 1);
        assertEquals(bookings.locator(".ag-row").count(), 2);                     // its own 2 rows, not all 4
        assertEquals(bookings.locator(".ag-cell:text-is('Ada')").count(), 0);      // Ada is in the other grid
        assertEquals(new GridLibrary(page, healer, "Customer").root().locator(".ag-cell:text-is('Ada')").count(), 1);
    }

    @Test
    public void gridChosenForTheWindowIsWhatPageObjectsUse() {
        TestSession session = new TestSession();
        session.bindToCurrentThread();
        try {
            PricePage price = new PricePage(page, healer);
            session.useGrid(page, "Customer");     // the step "I use the grid with column "Customer""
            assertEquals(price.grid().waitUntilLoaded().columnValues("Status"), List.of("Closed", "Open"));
            session.useGrid(page, "Booking");
            assertEquals(price.grid().waitUntilLoaded().columnValues("Status"), List.of("Open", "Closed"));
            session.useGrid(page, null);           // "I use the only grid": two grids, so it fails clearly
            expectThrows(AssertionError.class, () -> price.grid().waitUntilLoaded());
        } finally {
            session.close(false);                  // no browser was started: only unbinds
        }
        // outside a scenario no grid is chosen
        assertEquals(TestSession.chosenGridColumn(page), null);
    }

    @Test
    public void columnBothGridsHaveFailsClearly() {
        AssertionError error = expectThrows(AssertionError.class,
                () -> new GridLibrary(page, healer, "Status").waitUntilLoaded());
        assertTrue(error.getMessage().contains("2 grids have a 'Status' column"), error.getMessage());
    }

    /** A minimal AG Grid: header cells and cells with col-id, rows with row-index, a checkbox per row. */
    private static String grid(String firstHeader, List<String> first, String secondHeader, List<String> second) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < first.size(); i++) {
            rows.append("""
                    <div class="ag-row" row-index="%d">
                      <div class="ag-cell" col-id="c1">%s</div>
                      <div class="ag-cell" col-id="c2">%s</div>
                      <div class="ag-cell" col-id="sel"><input type="checkbox"></div>
                    </div>""".formatted(i, first.get(i), second.get(i)));
        }
        return """
                <div class="ag-root-wrapper">
                  <div class="ag-header">
                    <div class="ag-header-cell" col-id="c1"><span class="ag-header-cell-text">%s</span></div>
                    <div class="ag-header-cell" col-id="c2"><span class="ag-header-cell-text">%s</span></div>
                  </div>
                  <div class="ag-center-cols-container">%s</div>
                </div>""".formatted(firstHeader, secondHeader, rows);
    }
}
