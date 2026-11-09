package com.agenticqa.runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Entry point for all feature files. TestNG runs each Cucumber scenario as its own test, in parallel
 * (thread count: -Dthreads=N, default 4, set in pom.xml).
 * <ul>
 *   <li>Environment: -Denv=test (default dev)</li>
 *   <li>Select scenarios by tag: -Dcucumber.filter.tags="@smoke and @ui"</li>
 *   <li>Reports: target/reports/run-summary.html (overview, failures, heals) and cucumber.html (every step)</li>
 * </ul>
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"com.agenticqa.steps", "com.agenticqa.hooks"},
        plugin = {"pretty", "html:target/reports/cucumber.html", "json:target/reports/cucumber.json",
                "com.agenticqa.reporting.RunSummaryPlugin:target/reports/run-summary.html"},
        monochrome = true)
public class TestRunner extends AbstractTestNGCucumberTests {

    /**
     * Makes TestNG execute one Cucumber scenario per thread.
     *
     * @return one row per scenario
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
