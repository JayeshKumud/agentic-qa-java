package com.agenticqa.hooks;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import io.cucumber.datatable.DataTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * PLACEHOLDER for the company TestDataSetup, created before every scenario by
 * {@code TestInitializeHooks.setUpBaseData()} with the scenario's session. Paste the real implementation here.
 * <p>
 * Values put in the session here are read by the steps of the same scenario:
 * <pre>
 * session.data().put(Keys.ZONE_ID, zoneId);       // here
 * long zoneId = session.data().get(Keys.ZONE_ID); // in a step
 * </pre>
 */
public class TestDataSetup {

    private final TestSession session;

    /**
     * @param session the scenario's session, from TestInitializeHooks or PicoContainer
     */
    public TestDataSetup(TestSession session) {
        this.session = session;
    }

    /**
     * Puts the test data paths of a table in the session, each only if its key is in the table. The folder is
     * testdata.dir in common.properties (src/test/resources/testdata/):
     * <pre>
     * | BaseData | xyz        |   -> Keys.BASE_DATA_PATH = src/test/resources/testdata/xyz
     * | Scenario | scenaro001 |   -> Keys.SCENARIO_PATH  = src/test/resources/testdata/scenaro001
     * </pre>
     * {@link #resource(String)} turns one into a file path.
     *
     * @param table the Cucumber data table, first column = key, second column = value
     */
    public void putTestDataPaths(DataTable table) {
        Map<String, String> data = table.asMap(String.class, String.class);
        String testDataDir = Settings.getTestDataDir();

        if (data.containsKey("BaseData")) {
            session.data().put(Keys.BASE_DATA_PATH, testDataDir + data.get("BaseData"));
        }
        if (data.containsKey("Scenario")) {
            session.data().put(Keys.SCENARIO_PATH, testDataDir + data.get("Scenario"));
        }
    }

    /**
     * File path of test data, for example {@code resource(session.data().get(Keys.BASE_DATA_PATH))}. The path is
     * relative to the project root, so the run must start there (as mvn test does).
     *
     * @param path for example src/test/resources/testdata/xyz
     * @return the file
     * @throws IllegalArgumentException if the file does not exist
     */
    public static Path resource(String path) {
        Path file = Paths.get(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Test data not found: " + file.toAbsolutePath());
        }
        return file;
    }

    /** Placeholder for the tie-break setup, commented out in the hook as in the original. */
    public void tieBreak() {
        // nothing to prepare
    }
}
