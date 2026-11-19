package com.agenticqa.hooks;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;

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

    /** Row name of the base data in the test data table. */
    public static final String BASE_DATA = "BaseData";
    /** Row name of the scenario data in the test data table. */
    public static final String SCENARIO = "Scenario";

    private final TestSession session;

    /**
     * @param session the scenario's session, from TestInitializeHooks or PicoContainer
     */
    public TestDataSetup(TestSession session) {
        this.session = session;
    }

    /**
     * Stores the test data paths of a table such as
     * <pre>
     * | BaseData | xyz        |
     * | Scenario | scenaro001 |
     * </pre>
     * BaseData becomes {@link Keys#BASE_DATA_PATH} = testdata.baseDataPrefix + value, Scenario becomes
     * {@link Keys#SCENARIO_PATH} = testdata.scenarioPrefix + value. A row is stored only if its key is in the
     * table and has a value.
     *
     * @param table the rows of the data table, first column to second column
     */
    public void putTestDataPaths(Map<String, String> table) {
        String baseData = value(table, BASE_DATA);
        if (baseData != null) session.put(Keys.BASE_DATA_PATH, join(Settings.getBaseDataPrefix(), baseData));
        String scenario = value(table, SCENARIO);
        if (scenario != null) session.put(Keys.SCENARIO_PATH, join(Settings.getScenarioPrefix(), scenario));
    }

    /** Placeholder for the tie-break setup, commented out in the hook as in the original. */
    public void tieBreak() {
        // nothing to prepare
    }

    /** The trimmed value of a row, or null if the table has no such row or the value is blank. */
    private static String value(Map<String, String> table, String key) {
        if (table == null || !table.containsKey(key)) return null;
        String value = table.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** prefix + value with exactly one / between them. */
    static String join(String prefix, String value) {
        String p = prefix.replace('\\', '/');
        String v = value.replace('\\', '/');
        if (p.isEmpty()) return v;
        return (p.endsWith("/") ? p : p + "/") + (v.startsWith("/") ? v.substring(1) : v);
    }
}
