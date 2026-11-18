package com.agenticqa.runner;

import com.agenticqa.apis.AllureReportLogFilter;
import com.agenticqa.apis.ExtentReportLogFilter;
import com.agenticqa.config.Config;
import com.agenticqa.config.Settings;
import com.agenticqa.config.Settings.Mode;
import com.agenticqa.core.RunCleaner;
import com.agenticqa.core.RunLog;
import com.agenticqa.reporting.AllurePublisher;
import com.agenticqa.reporting.ArtifactoryPublisher;
import com.agenticqa.reporting.JsonReporter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point for all feature files, adapted from the existing framework's TestRunner. Each Cucumber scenario is
 * its own TestNG test.
 * <ul>
 *   <li>REST Assured, once for every API class (static block): relaxed HTTPS, HTTP client timeouts, and the
 *       Extent and Allure filters that log every request and response.</li>
 *   <li>Feature-aware runs: with -DfeatureAware=true only the scenarios {@link FeatureAwareManager} allows run.</li>
 *   <li>Parallel or sequential, per feature or scenario tag: {@code @Parallel} scenarios run side by side
 *       (-Dthreads=N, default 4, set in pom.xml); {@code @Sequential} scenarios run alone, one after the other,
 *       after the parallel ones. {@code @Sequential} wins when both apply. Untagged scenarios use
 *       {@code execution.defaultMode} of config/common.properties (parallel).</li>
 *   <li>Before the run, the logs and reports of the previous run are deleted (cleanup in config/common.properties;
 *       -Dcleanup.beforeRun=false keeps them) and the console output is copied to target/logs/run.log.</li>
 *   <li>After the run, in the pipeline, the reports are published; the test execution summary is printed.</li>
 *   <li>Environment: -Denv=test (dev, test, uat, ci; default dev)</li>
 *   <li>Select scenarios by tag: -Dcucumber.filter.tags="@SerialExecution" or "@smoke and @ui"</li>
 *   <li>Retries of failed scenarios: the retry section of the settings, or -Dretry.count=2
 *       -Dretry.tags="@ui" (see {@link ScenarioRetry})</li>
 *   <li>Run only the scenarios that failed last time (after retries):
 *       -Dcucumber.features=@target/reports/failed-scenarios.txt</li>
 *   <li>Reports in target/reports: run-summary.html (overview, failures, heals), cucumber.html (every step),
 *       extent/index.html (Extent Spark), test-execution-summary.json ({@link JsonReporter}); Allure results in
 *       target/allure-results (mvn allure:report)</li>
 * </ul>
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"com.agenticqa.steps", "com.agenticqa.hooks"},
        plugin = {"pretty",
                "com.agenticqa.reporting.ReportSetupPlugin",
                "com.agenticqa.reporting.JsonReporter",
                "html:target/reports/cucumber.html",
                "json:target/reports/cucumber.json",
                "com.agenticqa.reporting.RunSummaryPlugin:target/reports/run-summary.html",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
                "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"},
        monochrome = true)
public class TestRunner extends AbstractTestNGCucumberTests {
    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);
    private static final FeatureAwareManager featureAwareManager = new FeatureAwareManager();

    static {
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.config = RestAssured.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 90000)
                        .setParam("http.socket.timeout", 90000)
                        .setParam("http.connection-manager.timeout", 90000L) // a long, as HttpClient expects
                );
        RestAssured.filters(new ExtentReportLogFilter());
        RestAssured.filters(new AllureReportLogFilter());
        System.setProperty("com.amazonaws.sdk.disableCertChecking", "true");
        System.setProperty("aws.java.v1.disableDeprecationAnnouncement", "true");
    }

    /**
     * All scenarios, or with -DfeatureAware=true only those the feature flags allow. Both data providers below
     * choose from these.
     *
     * @return one row per scenario
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        if (!Config.isFeatureAware()) {
            return super.scenarios();
        } else {
            try {
                featureAwareManager.initializeFeatureSet();
                Object[][] allScenarios = super.scenarios();
                List<Object[]> featureAwareTestSet = new ArrayList<>();
                for (Object[] scenario : allScenarios) {
                    PickleWrapper wrapper = (PickleWrapper) scenario[0];
                    if (featureAwareManager.shouldRunTest(wrapper.getPickle())) {
                        featureAwareTestSet.add(scenario);
                    }
                }
                return featureAwareTestSet.toArray(new Object[0][0]);
            } catch (RuntimeException ex) {
                // if this exception is thrown, the test run will silently pass
                // reporting only 1 skipped test.
                // Instead, we manually exit execution and fail the build.
                logger.error("Some error encountered while retrieving feature flags: {}", ex.getMessage());
                System.exit(1);
                return null;
            }
        }
    }

    /**
     * Deletes the Allure results, reports and logs of the previous run (cleanup.folders in config/common.properties)
     * and starts the run log. Runs before {@code AbstractTestNGCucumberTests} creates Cucumber (in its
     * {@code @BeforeClass}), so before any report plugin opens its output file.
     */
    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        logger.info("Clearing Existing Allure Reports and Extent Reports");
        RunCleaner.clean(); // target/allure-results, target/reports (keeps the rerun file), ...
        RunLog.start(Settings.getLogFile());
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        ObjectNode testExecutionSummary = JsonReporter.testExecutionSummaryJO;
        try {
            if (Config.isRunningInPipeline()) {
                ArtifactoryPublisher.publishArtifactoryReports();
                AllurePublisher.publishAllureReports();
                testExecutionSummary.put("jfrog_report_url", ArtifactoryPublisher.JFROG_ARTIFACTORY_REPORT_URL);
                testExecutionSummary.put("allure_report_url", AllurePublisher.ALLURE_REPORT_URL);
                logger.info("JFROG ARTIFACTORY REPORT URL : {}", ArtifactoryPublisher.JFROG_ARTIFACTORY_REPORT_URL);
                logger.info("ALLURE REPORT URL : {}", AllurePublisher.ALLURE_REPORT_URL);
            }
        } catch (Exception e) {
            logger.error("Error during report publishing", e);
        } finally {
            System.out.println(testExecutionSummary);
            RunLog.stop();
        }
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
        Object[][] rows = Arrays.stream(scenarios()) // feature-aware, see scenarios()
                .filter(row -> Settings.modeOf(((PickleWrapper) row[0]).getPickle().getTags()) == mode)
                .toArray(Object[][]::new);
        System.out.printf("[EXECUTION] %d scenario(s) run %s%n", rows.length,
                mode == Mode.PARALLEL ? "in parallel" : "sequentially");
        return rows;
    }
}
