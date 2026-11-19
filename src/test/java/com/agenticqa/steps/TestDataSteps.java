package com.agenticqa.steps;

import com.agenticqa.hooks.TestDataSetup;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;

/**
 * Takes the test data table of a scenario:
 * <pre>
 * Given the test data is
 *   | BaseData | xyz        |
 *   | Scenario | scenaro001 |
 * </pre>
 * and stores the paths with {@link TestDataSetup#putTestDataPaths}, so later steps read them with
 * {@code session.get(Keys.BASE_DATA_PATH)} and {@code session.get(Keys.SCENARIO_PATH)}.
 */
public class TestDataSteps {

    private final TestDataSetup testData;

    /**
     * @param testData created by PicoContainer with this scenario's session
     */
    public TestDataSteps(TestDataSetup testData) {
        this.testData = testData;
    }

    @Given("the test data is")
    public void theTestDataIs(DataTable table) {
        testData.putTestDataPaths(table);
    }
}
