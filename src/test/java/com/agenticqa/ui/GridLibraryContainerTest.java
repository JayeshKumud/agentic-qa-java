package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * GridLibrary on a page with two grids that show the same columns, so no column tells them apart: each grid is
 * picked by the element it is in (a locator of the page, {@code locators/twogridstest.json} in the test resources).
 * No application needed.
 */
public class GridLibraryContainerTest {

    private static final String PAGE = """
            <html><body>
              <div id="left">%s</div>
              <div id="right">%s</div>
              <div id="empty"></div>
            </body></html>""".formatted(
            grid(List.of("Booking A", "Booking B"), List.of("Open", "Closed")),
            grid(List.of("Booking A", "Booking C"), List.of("Closed", "Open")));

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private Healer healer;
    private TwoGridsPage twoGrids;

    /** A page object over the test page, with the containers in locators/twogridstest.json. */
    public static final class TwoGridsPage implements PageComponents {
        private final Page page;
        private final PageElements el;

        public TwoGridsPage(Page page, Healer healer) {
            this.page = page;
            this.el = new PageElements(page, healer, "twogridstest");
        }

        @Override
        public String getTitle() {
            return page.title();
        }

        @Override
        public PageElements elements() {
            return el;
        }
    }

    @BeforeClass
    public void openPage() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        page.setContent(PAGE);
        healer = new Healer();
        twoGrids = new TwoGridsPage(page, healer);
    }

    @AfterClass(alwaysRun = true)
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void eachGridIsFoundByItsContainer() {
        GridLibrary left = twoGrids.gridIn("leftGrid").waitUntilLoaded();     // the panel around the grid
        GridLibrary right = twoGrids.gridIn("rightGrid").waitUntilLoaded();   // the grid element itself

        assertEquals(left.columnValues("Booking"), List.of("Booking A", "Booking B"));
        assertEquals(right.columnValues("Booking"), List.of("Booking A", "Booking C"));
        // row-index 0 shows "Booking A" in both grids; each grid returns only its own row
        assertEquals(left.values(Map.of("Booking", "Booking A"), "Status"), List.of("Open"));
        assertEquals(right.values(Map.of("Booking", "Booking A"), "Status"), List.of("Closed"));
        assertEquals(left.rows(Map.of("Booking", "Booking A")).count(), 1);
        assertEquals(left.root().count(), 1);
        assertEquals(right.root().count(), 1);
    }

    @Test
    public void containerWithLocatorDirectly() {
        GridLibrary left = new GridLibrary(page, healer, page.locator("#left"), "#left").waitUntilLoaded();
        assertEquals(left.columnValues("Status"), List.of("Open", "Closed"));
    }

    @Test
    public void columnCannotPickThemButTheMessageNamesTheContainerWay() {
        AssertionError error = expectThrows(AssertionError.class, () -> twoGrids.grid("Booking").waitUntilLoaded());
        assertTrue(error.getMessage().contains("2 grids have a 'Booking' column"), error.getMessage());
        assertTrue(error.getMessage().contains("I use the grid in"), error.getMessage());
    }

    @Test
    public void containerWithBothGridsFailsClearly() {
        AssertionError error = expectThrows(AssertionError.class,
                () -> twoGrids.gridIn("bothPanels").waitUntilLoaded());
        assertTrue(error.getMessage().contains("2 grids in 'bothPanels'"), error.getMessage());
    }

    @Test
    public void containerWithoutGridFailsClearly() {
        // waits ui.waits.assertionMs for a grid that never comes
        AssertionError error = expectThrows(AssertionError.class, () -> twoGrids.gridIn("noGrid").waitUntilLoaded());
        assertTrue(error.getMessage().contains("the grid in 'noGrid'"), error.getMessage());
    }

    @Test
    public void unknownContainerNameFailsAtOnce() {
        expectThrows(IllegalArgumentException.class, () -> twoGrids.gridIn("noSuchLocator"));
    }

    @Test
    public void containerChosenForTheWindowIsWhatPageObjectsUse() {
        TestSession session = new TestSession();
        session.bindToCurrentThread();
        try {
            session.useGridIn(page, "rightGrid");   // the step "I use the grid in "rightGrid""
            assertEquals(twoGrids.grid().waitUntilLoaded().columnValues("Booking"), List.of("Booking A", "Booking C"));
            session.useGridIn(page, "leftGrid");
            assertEquals(twoGrids.grid().waitUntilLoaded().columnValues("Booking"), List.of("Booking A", "Booking B"));

            session.useGrid(page, "Booking");       // a column replaces the container
            assertNull(session.gridContainer(page));
            session.useGridIn(page, "leftGrid");    // and a container replaces the column
            assertNull(session.gridColumn(page));

            session.useGrid(page, null);            // "I use the only grid": clears the container too
            assertNull(session.gridContainer(page));
            expectThrows(AssertionError.class, () -> twoGrids.grid().waitUntilLoaded());
        } finally {
            session.close(false);                   // no browser was started: only unbinds
        }
        assertNull(TestSession.chosenGridContainer(page));
    }

    /** A minimal AG Grid with the columns Booking and Status: header cells and cells with col-id, rows with row-index. */
    private static String grid(List<String> bookings, List<String> statuses) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < bookings.size(); i++) {
            rows.append("""
                    <div class="ag-row" row-index="%d">
                      <div class="ag-cell" col-id="booking">%s</div>
                      <div class="ag-cell" col-id="status">%s</div>
                      <div class="ag-cell" col-id="sel"><input type="checkbox"></div>
                    </div>""".formatted(i, bookings.get(i), statuses.get(i)));
        }
        return """
                <div class="ag-root-wrapper">
                  <div class="ag-header">
                    <div class="ag-header-cell" col-id="booking"><span class="ag-header-cell-text">Booking</span></div>
                    <div class="ag-header-cell" col-id="status"><span class="ag-header-cell-text">Status</span></div>
                  </div>
                  <div class="ag-center-cols-container">%s</div>
                </div>""".formatted(rows);
    }
}
