package com.agenticqa.hooks;

import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class TestDataSetupTest {

    @Test
    public void putsBothPathsWithTheirPrefix() {
        TestSession session = new TestSession();
        new TestDataSetup(session).putTestDataPaths(Map.of("BaseData", "xyz", "Scenario", "scenaro001"));
        assertEquals(session.get(Keys.BASE_DATA_PATH), "src/test/resources/testdata/xyz");
        assertEquals(session.get(Keys.SCENARIO_PATH), "src/test/resources/testdata/scenaro001");
    }

    @Test
    public void putsOnlyTheKeysThatAreInTheTable() {
        TestSession session = new TestSession();
        new TestDataSetup(session).putTestDataPaths(Map.of("Scenario", "scenaro001", "Other", "x"));
        assertFalse(session.has(Keys.BASE_DATA_PATH), "no BaseData row");
        assertEquals(session.get(Keys.SCENARIO_PATH), "src/test/resources/testdata/scenaro001");

        TestSession blank = new TestSession();
        new TestDataSetup(blank).putTestDataPaths(Map.of("BaseData", " "));
        assertFalse(blank.has(Keys.BASE_DATA_PATH), "blank value");
    }

    @Test
    public void joinsWithOneSlash() {
        assertEquals(TestDataSetup.join("src/test/resources/testdata", "xyz"), "src/test/resources/testdata/xyz");
        assertEquals(TestDataSetup.join("src/test/resources/testdata/", "/xyz"), "src/test/resources/testdata/xyz");
        assertEquals(TestDataSetup.join("src\\test\\resources\\testdata\\", "a\\b"), "src/test/resources/testdata/a/b");
    }
}
