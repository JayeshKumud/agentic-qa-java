package com.agenticqa.runner;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Entry point for all feature files. TestNG runs each Cucumber scenario as its own test, in parallel
 * (thread count: -Dthreads=N, default 4, set in pom.xml; -Dthreads=1 runs one scenario at a time).
 * <ul>
 *   <li>Environment: -Denv=test (dev, test, uat, ci; default dev)</li>
 *   <li>Select scenarios by tag: -Dcucumber.filter.tags="@smoke and @ui"</li>
 *   <li>Retries of failed scenarios: the retry section of the environment file, or -Dretry.count=2
 *       -Dretry.tags="@ui" (see {@link ScenarioRetry})</li>
 *   <li>Run only the scenarios that failed last time (after retries):
 *       -Dcucumber.features=@target/reports/failed-scenarios.txt</li>
 *   <li>Reports in target/reports: run-summary.html (overview, failures, heals), cucumber.html (every step),
 *       extent/index.html (Extent Spark); Allure results in target/allure-results (mvn allure:report)</li>
 * </ul>
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"com.agenticqa.steps", "com.agenticqa.hooks"},
        plugin = {"pretty",
                "com.agenticqa.reporting.ReportSetupPlugin",
                "html:target/reports/cucumber.html",
                "json:target/reports/cucumber.json",
                "com.agenticqa.reporting.RunSummaryPlugin:target/reports/run-summary.html",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
                "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"},
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

    /**
     * Runs one scenario. Overridden only to attach {@link ScenarioRetry}, so a failed scenario can run again.
     *
     * @param pickleWrapper  the scenario
     * @param featureWrapper its feature
     */
    @Override
    @Test(groups = "cucumber", description = "Runs Cucumber Scenarios", dataProvider = "scenarios",
            retryAnalyzer = ScenarioRetry.class)
    public void runScenario(PickleWrapper pickleWrapper, FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }
}
