package com.agenticqa.hooks;

import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class TestDataSetupTest {

    /** A data table as Cucumber builds it, with the converter that asMap needs. */
    private static DataTable table(List<List<String>> rows) {
        return DataTable.create(rows, new DataTableTypeRegistryTableConverter(new DataTableTypeRegistry(Locale.ENGLISH)));
    }

    @Test
    public void putsBothPathsUnderSrcTestResourcesTestdata() {
        TestSession session = new TestSession();
        new TestDataSetup(session).putTestDataPaths(table(List.of(
                List.of("BaseData", "xyz"),
                List.of("Scenario", "scenaro001"))));
        assertEquals(session.data().get(Keys.BASE_DATA_PATH), "src/test/resources/testdata/xyz");
        assertEquals(session.data().get(Keys.SCENARIO_PATH), "src/test/resources/testdata/scenaro001");
    }

    @Test
    public void putsOnlyTheKeysThatAreInTheTable() {
        TestSession session = new TestSession();
        new TestDataSetup(session).putTestDataPaths(table(List.of(
                List.of("Scenario", "scenaro001"))));
        assertFalse(session.data().has(Keys.BASE_DATA_PATH), "no BaseData row");
        assertEquals(session.data().get(Keys.SCENARIO_PATH), "src/test/resources/testdata/scenaro001");
    }

    @Test
    public void folderComesFromTestdataDirAndCanBeOverridden() {
        System.setProperty("testdata.dir", "other/data");
        try {
            TestSession session = new TestSession();
            new TestDataSetup(session).putTestDataPaths(table(List.of(List.of("BaseData", "xyz"))));
            assertEquals(session.data().get(Keys.BASE_DATA_PATH), "other/data/xyz", "a / is added when missing");
        } finally {
            System.clearProperty("testdata.dir");
        }
    }

    @Test
    public void resourceFindsAFileFromTheProjectRoot() {
        Path file = TestDataSetup.resource("src/test/resources/simplelogger.properties");
        assertTrue(Files.isRegularFile(file), file.toString());
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> TestDataSetup.resource("src/test/resources/testdata/no-such-file"));
        assertTrue(e.getMessage().contains("no-such-file"), e.getMessage());
    }
}
