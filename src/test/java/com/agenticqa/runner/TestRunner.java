package com.agenticqa.runner;

import com.agenticqa.apis.RestAssuredSetup;
import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;
import com.agenticqa.config.Settings.Execution.Mode;
import com.agenticqa.core.RunCleaner;
import com.agenticqa.core.RunLog;
import com.agenticqa.reporting.ReportPublisher;
import com.agenticqa.reporting.TestExecutionResults;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

/**
 * Entry point for all feature files. Each Cucumber scenario is its own TestNG test.
 * <ul>
 *   <li>Parallel or sequential, per feature or scenario tag: {@code @Parallel} scenarios run side by side
 *       (-Dthreads=N, default 4, set in pom.xml); {@code @Sequential} scenarios run alone, one after the other,
 *       after the parallel ones. {@code @Sequential} wins when both apply. Untagged scenarios use
 *       {@code execution.defaultMode} of config/common.yaml (parallel).</li>
 *   <li>Before the run, the logs and reports of the previous run are deleted (cleanup in config/common.yaml;
 *       -Dcleanup.beforeRun=false keeps them) and the console output is copied to target/logs/run.log.</li>
 *   <li>Environment: -Denv=test (dev, test, uat, ci; default dev)</li>
 *   <li>Select scenarios by tag: -Dcucumber.filter.tags="@smoke and @ui"</li>
 *   <li>Retries of failed scenarios: the retry section of the settings, or -Dretry.count=2
 *       -Dretry.tags="@ui" (see {@link ScenarioRetry})</li>
 *   <li>Run only the scenarios that failed last time (after retries):
 *       -Dcucumber.features=@target/reports/failed-scenarios.txt</li>
 *   <li>Reports in target/reports: run-summary.html (overview, failures, heals), cucumber.html (every step),
 *       extent/index.html (Extent Spark), test-execution-summary.json (one result per test case, printed after
 *       the run); Allure results in target/allure-results (mvn allure:report)</li>
 *   <li>REST Assured: relaxed HTTPS and HTTP client timeouts for every API class, from the api section</li>
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

    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);

    /**
     * Deletes the logs and reports of the previous run, starts the run log and applies the global REST Assured
     * setup (api section of config/common.yaml). Runs before {@code AbstractTestNGCucumberTests} creates Cucumber
     * (in its {@code @BeforeClass}), so before any report plugin opens its output file.
     */
    @BeforeSuite(alwaysRun = true)
    public void prepareRun() {
        Settings settings = EnvConfig.get();
        Settings.Cleanup cleanup = settings.cleanup();
        RunCleaner.clean(cleanup);
        RunLog.start(cleanup.logFile());
        RestAssuredSetup.configure(settings.api());
    }

    /**
     * Writes the test execution summary (one result per scenario, see {@link TestExecutionResults}). In the
     * pipeline, the configured {@link ReportPublisher} publishes the reports first and its URLs are added to the
     * summary. A publishing failure is logged and never stops the summary. Then the run log is closed.
     */
    @AfterSuite(alwaysRun = true)
    public void finishRun() {
        Settings.Reporting reporting = EnvConfig.get().reporting();
        Map<String, Object> summary = TestExecutionResults.summary();
        try {
            if (isRunningInPipeline() && !reporting.publisher().isEmpty()) {
                Map<String, String> urls = ReportPublisher.create(reporting.publisher()).publish();
                summary.putAll(urls);
                urls.forEach((name, url) -> logger.info("{}: {}", name, url));
            }
        } catch (Exception e) {
            logger.error("Error during report publishing", e);
        } finally {
            try {
                TestExecutionResults.write(summary, Paths.get(reporting.summaryFile()));
                System.out.println("[SUMMARY] " + Paths.get(reporting.summaryFile()).toAbsolutePath()
                        + System.lineSeparator() + TestExecutionResults.toJson(summary));
            } catch (RuntimeException e) {
                logger.error("Could not write the test execution summary", e);
            }
            RunLog.stop();
        }
    }

    /**
     * Tells whether the run is in the pipeline: the CI environment variable is set (GitHub Actions, GitLab and
     * Jenkins plugins set it), or the environment is ci (-Denv=ci).
     *
     * @return true in the pipeline
     */
    static boolean isRunningInPipeline() {
        String ci = System.getenv("CI");
        return (ci != null && !ci.isBlank() && !ci.equalsIgnoreCase("false")) || "ci".equals(EnvConfig.envName());
    }

    /**
     * The scenarios that run in parallel: tagged @Parallel, or untagged when the default mode is parallel.
     *
     * @return one row per scenario
     */
    @DataProvider(parallel = true)
    public Object[][] parallelScenarios() {
        return select(Mode.PARALLEL);
    }

    /**
     * The scenarios that run one at a time: tagged @Sequential, or untagged when the default mode is sequential.
     *
     * @return one row per scenario
     */
    @DataProvider(parallel = false)
    public Object[][] sequentialScenarios() {
        return select(Mode.SEQUENTIAL);
    }

    /**
     * Runs one parallel scenario. A failed scenario can run again ({@link ScenarioRetry}).
     *
     * @param pickleWrapper  the scenario
     * @param featureWrapper its feature
     */
    @Test(groups = "cucumber", description = "Runs parallel Cucumber scenarios", dataProvider = "parallelScenarios",
            retryAnalyzer = ScenarioRetry.class, priority = 0)
    public void runParallelScenario(PickleWrapper pickleWrapper, FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }

    /**
     * Runs one sequential scenario, after all parallel ones, with no other scenario running at the same time.
     *
     * @param pickleWrapper  the scenario
     * @param featureWrapper its feature
     */
    @Test(groups = "cucumber", description = "Runs sequential Cucumber scenarios",
            dataProvider = "sequentialScenarios", retryAnalyzer = ScenarioRetry.class, priority = 1)
    public void runSequentialScenario(PickleWrapper pickleWrapper, FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }

    /**
     * Disabled: the base class's single test method is replaced by the two above.
     *
     * @param pickleWrapper  the scenario
     * @param featureWrapper its feature
     */
    @Override
    @Test(enabled = false)
    public void runScenario(PickleWrapper pickleWrapper, FeatureWrapper featureWrapper) {
        super.runScenario(pickleWrapper, featureWrapper);
    }

    private Object[][] select(Mode mode) {
        Settings.Execution execution = EnvConfig.get().execution();
        Object[][] rows = Arrays.stream(super.scenarios())
                .filter(row -> execution.modeOf(((PickleWrapper) row[0]).getPickle().getTags()) == mode)
                .toArray(Object[][]::new);
        System.out.printf("[EXECUTION] %d scenario(s) run %s%n", rows.length,
                mode == Mode.PARALLEL ? "in parallel" : "sequentially");
        return rows;
    }
}
