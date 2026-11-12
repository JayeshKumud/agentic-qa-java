package com.agenticqa.ui;

import com.agenticqa.ui.LocatorRepository.Definition;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class LocatorRepositoryTest {

    @Test
    public void readsHealingLocatorsWithTheirFingerprintKey() {
        Definition search = LocatorRepository.get("customers", "searchInput");
        assertEquals(search.key(), "customers.searchInput");
        assertEquals(search.selector(), "#search-input");
        assertEquals(search.description(), "search input");
        assertTrue(search.heal());
    }

    @Test
    public void readsPlainLocatorsAndSkipsComments() {
        Definition rows = LocatorRepository.get("customers", "rows");
        assertFalse(rows.heal());
        assertEquals(rows.description(), "rows");
        assertFalse(LocatorRepository.page("customers").containsKey("_comment"));
    }

    @Test
    public void listsTheLocatorsWhenANameIsUnknown() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LocatorRepository.get("customers", "serchInput"));
        assertTrue(e.getMessage().contains("searchInput"), e.getMessage());
    }

    @Test
    public void reportsAMissingFile() {
        expectThrows(IllegalArgumentException.class, () -> LocatorRepository.page("no-such-page"));
    }

    private static final List<String> CUSTOMERS_PAGE = List.of("customers", "customer-form", "pagination", "common");

    @Test
    public void findsANameInAnyFileOfThePage() {
        assertEquals(LocatorRepository.find(CUSTOMERS_PAGE, "searchInput").key(), "customers.searchInput");
        assertEquals(LocatorRepository.find(CUSTOMERS_PAGE, "addButton").key(), "customer-form.addButton");
        assertEquals(LocatorRepository.find(CUSTOMERS_PAGE, "nextLink").key(), "pagination.nextLink");
        assertEquals(LocatorRepository.find(CUSTOMERS_PAGE, "heading").selector(), "h1");
    }

    @Test
    public void aQualifiedNamePicksOneFile() {
        assertEquals(LocatorRepository.find(CUSTOMERS_PAGE, "pagination.nextLink").selector(), "#next-page-link");
        assertEquals(LocatorRepository.find(List.of("customers"), "common.heading").selector(), "h1",
                "the file need not be one of the page's files");
        expectThrows(IllegalArgumentException.class,
                () -> LocatorRepository.find(CUSTOMERS_PAGE, "customers.nextLink"));
    }

    @Test
    public void anUnknownNameListsTheNamesOfEveryFile() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LocatorRepository.find(CUSTOMERS_PAGE, "nextLnk"));
        assertTrue(e.getMessage().contains("locators/pagination.json: [pageIndicator, nextLink, previousLink]"),
                e.getMessage());
        assertTrue(e.getMessage().contains("locators/customer-form.json"), e.getMessage());
    }
}
