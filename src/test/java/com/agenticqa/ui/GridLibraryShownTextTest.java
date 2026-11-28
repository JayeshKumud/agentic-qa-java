package com.agenticqa.ui;

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
import static org.testng.Assert.assertTrue;

/**
 * GridLibrary matches what a cell shows (Playwright's innerText), not its textContent: hidden text in a cell, such
 * as AG Grid's accessibility labels, is left out, and CSS text-transform is applied. No application needed.
 */
public class GridLibraryShownTextTest {

    private static final String PAGE = """
            <html><body>
              <div class="ag-root-wrapper">
                <div class="ag-header">
                  <div class="ag-header-cell" col-id="b"><span class="ag-header-cell-text">Booking</span></div>
                  <div class="ag-header-cell" col-id="s"><span class="ag-header-cell-text">Status</span></div>
                </div>
                <div class="ag-center-cols-container">
                  <div class="ag-row" row-index="0">
                    <div class="ag-cell" col-id="b">
                      <span style="display:none">Press Space to toggle row selection</span>
                      <span>Booking A</span>
                    </div>
                    <div class="ag-cell" col-id="s"><span style="text-transform:uppercase">open</span></div>
                  </div>
                  <div class="ag-row" row-index="1">
                    <div class="ag-cell" col-id="b"><input type="text" value="Booking B"></div>
                    <div class="ag-cell" col-id="s">
                      <select><option>Open</option><option selected>Closed</option></select>
                    </div>
                  </div>
                  <div class="ag-row" row-index="2">
                    <div class="ag-cell" col-id="b"><textarea>Booking C</textarea></div>
                    <div class="ag-cell" col-id="s"><input type="checkbox"> <span>Open</span></div>
                  </div>
                  <div class="ag-row" row-index="3">
                    <div class="ag-cell" col-id="b"><input id="typed" type="text"></div>
                    <div class="ag-cell" col-id="s">Open</div>
                  </div>
                </div>
              </div>
            </body></html>""";

    private Playwright playwright;
    private Browser browser;
    private GridLibrary grid;

    @BeforeClass
    public void openPage() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = browser.newPage();
        page.setContent(PAGE);
        page.locator("#typed").fill("Booking D");   // a value typed in, not in the HTML
        grid = new GridLibrary(page, new Healer()).waitUntilLoaded();
    }

    @Test
    public void inputTextareaAndDropdownCellsMatchByTheirValue() {
        assertEquals(grid.cell("Booking", "Booking B").count(), 1);                      // text input
        assertEquals(grid.cell("Booking", "Booking C").count(), 1);                      // textarea
        assertEquals(grid.cell("Booking", "Booking D").count(), 1);                      // typed by the test
        assertEquals(grid.values(Map.of("Booking", "Booking B"), "Status"), List.of("Closed")); // selected option
        assertEquals(grid.cell("Status", "Closed").count(), 1);
        assertEquals(grid.values(Map.of("Booking", "Booking C"), "Status"), List.of("Open"));   // checkbox ignored
        assertEquals(grid.columnValues("Booking"), List.of("Booking A", "Booking B", "Booking C", "Booking D"));
    }

    @AfterClass(alwaysRun = true)
    public void close() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void hiddenTextInACellIsIgnored() {
        assertEquals(grid.cell("Booking", "Booking A").count(), 1);
        assertTrue(grid.contains("Booking", "Booking A"));
        assertEquals(grid.rows(Map.of("Booking", "Booking A")).count(), 1);
        assertEquals(grid.values(Map.of("Booking", "Booking A"), "Booking"), List.of("Booking A"));
    }

    @Test
    public void textIsMatchedAsShown() {
        assertEquals(grid.values(Map.of("Booking", "Booking A"), "Status"), List.of("OPEN"));
        assertEquals(grid.cell("Status", "OPEN").count(), 1);
    }
}
