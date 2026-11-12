package com.agenticqa.ui;

import com.agenticqa.ui.LocatorRepository.Definition;
import org.testng.annotations.Test;

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
}
