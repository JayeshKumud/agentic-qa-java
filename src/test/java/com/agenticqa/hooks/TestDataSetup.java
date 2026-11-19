package com.agenticqa.hooks;

import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import io.cucumber.datatable.DataTable;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * PLACEHOLDER for the company TestDataSetup, created before every scenario by
 * {@code TestInitializeHooks.setUpBaseData()} with the scenario's session. Paste the real implementation here.
 * <p>
 * Values put in the session here are read by the steps of the same scenario:
 * <pre>
 * session.put(Keys.ZONE_ID, zoneId);       // here
 * long zoneId = session.get(Keys.ZONE_ID); // in a step
 * </pre>
 */
public class TestDataSetup {

    /** Classpath folder of the test data: src/test/resources/testdata. */
    public static final String TEST_DATA = "testdata/";

    private final TestSession session;

    /**
     * @param session the scenario's session, from TestInitializeHooks or PicoContainer
     */
    public TestDataSetup(TestSession session) {
        this.session = session;
    }

    /**
     * Puts the test data paths of a table in the session, each only if its key is in the table:
     * <pre>
     * | BaseData | xyz        |   -> Keys.BASE_DATA_PATH = testdata/xyz
     * | Scenario | scenaro001 |   -> Keys.SCENARIO_PATH  = testdata/scenaro001
     * </pre>
     * The paths are classpath resources; {@link #resource(String)} turns one into a file path.
     *
     * @param table the Cucumber data table, first column = key, second column = value
     */
    public void putTestDataPaths(DataTable table) {
        Map<String, String> data = table.asMap(String.class, String.class);

        if (data.containsKey("BaseData")) {
            session.put(Keys.BASE_DATA_PATH, TEST_DATA + data.get("BaseData"));
        }
        if (data.containsKey("Scenario")) {
            session.put(Keys.SCENARIO_PATH, TEST_DATA + data.get("Scenario"));
        }
    }

    /**
     * File path of a classpath resource, for example {@code resource(session.get(Keys.BASE_DATA_PATH))}.
     *
     * @param path the resource, for example testdata/xyz (src/test/resources/testdata/xyz)
     * @return the file, under target/test-classes
     * @throws IllegalArgumentException if the resource does not exist
     */
    public static Path resource(String path) {
        URL url = TestDataSetup.class.getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Test data not found on the classpath: " + path
                    + " (expected src/test/resources/" + path + ")");
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Bad resource path: " + url, e);
        }
    }

    /** Placeholder for the tie-break setup, commented out in the hook as in the original. */
    public void tieBreak() {
        // nothing to prepare
    }
}
