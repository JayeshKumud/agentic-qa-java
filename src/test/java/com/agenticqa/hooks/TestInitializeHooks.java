package com.agenticqa.hooks;

import com.agenticqa.api.ApiFactory;
import com.agenticqa.apis.EnergyProfileCalculatorApi;
import com.agenticqa.apis.UnitManagerApi;
import com.agenticqa.core.TestSession;
import com.agenticqa.reporting.TestExecutionResult;
import com.agenticqa.steps.Keys;
import com.agenticqa.ui.WindowNavigator;
import com.agenticqa.ui.healing.ElementLookup;
import com.agenticqa.ui.healing.HealEvent;
import com.microsoft.playwright.Page;
import com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The pre and post actions of every scenario and step, adapted from the existing framework's TestInitializeHooks.
 * Differences, because the browser here belongs to the scenario's {@link TestSession} (PicoContainer injects it):
 * <ul>
 *   <li>{@link #bindSession} (order 1) binds the session first, so {@code TestSession.current()} works in every
 *       later hook and step.</li>
 *   <li>BrowserFactory / PageComponentsFactory calls are TestSession calls; {@code session.close} stops the trace
 *       and saves the video, so stopTraces is part of {@link #closeBrowser}.</li>
 *   <li>{@code session.close} also deletes this scenario's own raw video recording, never the whole traces or
 *       videos folder (that would delete the files of scenarios running in parallel): {@code RunCleaner} empties
 *       target/artifacts once, before the run.</li>
 *   <li>No ScenarioContext.clear(): the session and its data end with the scenario.</li>
 * </ul>
 * Run order: the order number is the hook's place in the sequence. {@code @Before} runs lowest first (1, 2, 3);
 * {@code @After} runs highest first, so its numbers count down (7 runs first, 1 runs last):
 * <pre>
 * &#64;Before  1   bindSession            session usable by everything after it
 * &#64;Before  2   checkTags              skip or fail early, before any setup work
 * &#64;Before  3   setUpBaseData
 * &#64;AfterStep   afterStep              screenshot of the current window after every step
 * &#64;After   7   attachHealsAndFailureScreenshot   evidence first, while the page is open
 * &#64;After   6   collectDebugInformation           reads the page and the last API response
 * &#64;After   5   saveVideo              failed only: closes the context so the video is complete
 * &#64;After   4   closeBrowser           trace, video (raw recording deleted), close the context
 * &#64;After   2   ServiceCleanup.cleanUp delete the data the steps registered, newest first
 * &#64;After   1   teardownData           delete the zone, then record the result (last, so it has the full duration)
 * </pre>
 */
public class TestInitializeHooks {
    private static final Logger logger = LoggerFactory.getLogger(TestInitializeHooks.class);

    /** Result of every scenario of the run, by test id; written by {@code JsonReporter}. Scenarios run in parallel. */
    public static final Map<String, TestExecutionResult> testExecutionMap =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private final TestSession session;
    private final WindowNavigator windows;
    private long featureStartTime;
    /** How many element lookups a failed step logs, newest first. */
    private static final int LOOKUPS_LOGGED = 5;
    /** True once the failed step's element lookups were logged; later steps are skipped, not failed. */
    private boolean lookupsLogged;

    /**
     * Receives this scenario's session and window navigator from PicoContainer.
     *
     * @param session the per-scenario session
     * @param windows knows which window the scenario is on, so screenshots show that window
     */
    public TestInitializeHooks(TestSession session, WindowNavigator windows) {
        this.session = session;
        this.windows = windows;
    }

    /**
     * Binds the scenario's session to this thread, so {@code TestSession.current()} finds it, and starts the
     * scenario's clock. Runs first (order 1).
     *
     * @param scenario the running scenario
     */
    @Before(order = 1)
    public void bindSession(Scenario scenario) {
        featureStartTime = System.currentTimeMillis();
        session.scenarioName(scenario.getName());
        session.bindToCurrentThread();
    }

    /**
     * Skips a {@code @MUTED} scenario and fails one without a valid {@code @PRGBATR-} tag, before any setup work.
     *
     * @param scenario the running scenario
     */
    @Before(order = 2)
    public void checkTags(Scenario scenario) {
        List<String> tags = new ArrayList<>(scenario.getSourceTagNames());
        logger.info("Tags found - {} ", tags);
        // RetryContext.setTags(tags) is not needed: ScenarioRetry reads the scenario's tags itself
        boolean isMuted = tags.stream().map(String::toUpperCase).anyMatch(t -> t.contains("@MUTED"));
        if (isMuted) {
            logger.info("This test is muted. Hence, skipping this test....");
            throw new SkipException("Skipping this test");
        }
        if (tags.isEmpty()) {
            logger.error("No tags found...");
            throw new AssertionError("No tags found. Expected a tag starting with @PRGBATR- followed by 4-6 digits.");
        }
        String jiraTCTag = null;
        for (String t : tags) {
            if (t != null && t.startsWith("@PRGBATR-")) {
                jiraTCTag = t.trim();
                break;
            }
        }
        if (jiraTCTag == null) {
            logger.error("Missing required @PRGBATR- tag. Tags found - {} ", tags);
            throw new AssertionError("Missing required @PRGBATR- jira test case tag.");
        }
        Pattern pattern = Pattern.compile("^@PRGBATR-\\d{4,6}$", Pattern.CASE_INSENSITIVE);
        if (!pattern.matcher(jiraTCTag).matches()) {
            logger.error("Invalid tag format. The first tag that starts with @PRGBATR- must be exactly @PRGBATR- followed by 4-6 digits. Found: " + jiraTCTag);
            throw new AssertionError(
                    "Invalid tag format. The first tag that starts with @PRGBATR- must be exactly @PRGBATR- followed by 4-6 digits. Found: " + jiraTCTag
            );
        }
    }


    /** Prepares the base test data ({@link TestDataSetup}, placeholder). */
    @Before(order = 3)
    public void setUpBaseData() {
        TestDataSetup testDataSetup = new TestDataSetup(session);
        // testDataSetup.tieBreak();
    }

    /**
     * Logs the failed step's element lookups and attaches a screenshot of the window the scenario is on (the home
     * page, or the window the last switch opened), so a step on a Price Booking or Customer window shows that window.
     *
     * @param scenario the running scenario
     */
    @AfterStep
    public void afterStep(Scenario scenario) {
        logElementLookupsOfFailedStep(scenario);
        if (session.hasPage()) {
            try {
                String screenshotName = scenario.getName().replaceAll(" ", "");
                byte[] screenshot = currentWindow().screenshot();

                scenario.attach(screenshot, "image/png", screenshotName);
            } catch (Exception e) {
                logger.error("Failed to take screenshot: {}", e.getMessage());
            }
        }
    }


    /**
     * The window the scenario is on ({@link WindowNavigator#current()}); the first window if that one was closed.
     * Call it only when {@code session.hasPage()}, so no browser is opened for it.
     */
    private Page currentWindow() {
        Page window = windows.current().window();
        return window.isClosed() ? session.page() : window;
    }

    /**
     * When a step fails, logs the elements it looked up last (newest first) to the Extent, Allure and Cucumber
     * reports and the console: element name, selector, what the lookup found or why healing failed, and how many
     * elements the selector matches now. A bare Playwright timeout then shows which element it waited for.
     *
     * @param scenario the running scenario
     */
    private void logElementLookupsOfFailedStep(Scenario scenario) {
        if (lookupsLogged || !scenario.isFailed()) return;
        lookupsLogged = true;
        List<ElementLookup> lookups = new ArrayList<>(session.elementLookups());
        if (lookups.isEmpty()) return;
        Collections.reverse(lookups);
        String text = "Elements looked up last before the failure (newest first; the failed element is usually the first):"
                + System.lineSeparator()
                + lookups.stream().limit(LOOKUPS_LOGGED).map(ElementLookup::describe)
                        .collect(Collectors.joining(System.lineSeparator()));
        logger.error(text);
        scenario.attach(text, "text/plain", "element lookups"); // cucumber.html and Allure
        if (ExtentCucumberAdapter.getCurrentStep() != null) {
            ExtentCucumberAdapter.addTestStepLog("<pre>" + text.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;") + "</pre>");
        }
    }

    /**
     * Attaches the healed locators, and a full-page screenshot of the current window if the scenario failed, while
     * the page is still open.
     *
     * @param scenario the finished scenario
     */
    @After(order = 7)
    public void attachHealsAndFailureScreenshot(Scenario scenario) {
        List<HealEvent> heals = session.healEvents();
        if (!heals.isEmpty()) {
            String text = heals.stream().map(HealEvent::summary).collect(Collectors.joining(System.lineSeparator()));
            scenario.attach("Locators healed - update the locator file (src/main/resources/locators):"
                    + System.lineSeparator() + text, "text/plain", "self-healing");
        }
        if (scenario.isFailed() && session.hasPage()) {
            try {
                byte[] png = currentWindow().screenshot(new Page.ScreenshotOptions().setFullPage(true));
                scenario.attach(png, "image/png", "failure-screenshot");
            } catch (RuntimeException ignored) {
                // a missing screenshot must not hide the real failure
            }
        }
    }

    @After(order = 6)
    public void collectDebugInformation(Scenario scenario) {
        if (scenario.isFailed()) {
            new DebugInformationCollector().collect();
        }
    }

    @After(order = 5)
    public void saveVideo(Scenario scenario) {
        if (scenario.isFailed() && session.hasPage()) {
            // the video is complete only after the browser context is closed, so close it here;
            // closeBrowser (order 4) then finds nothing left to close
            closeBrowser(scenario);
        }
    }

    /**
     * Closes the scenario's browser context (the browser stays open for the next scenario on this thread). For a
     * failed scenario, the trace zip and the video are kept, their paths attached, and the files attached to
     * Allure. Calling it a second time does nothing.
     *
     * @param scenario the finished scenario
     */
    @After(order = 4)
    public void closeBrowser(Scenario scenario) {
        TestSession.SavedFiles saved = session.close(scenario.isFailed());
        String name = scenario.getName().replaceAll(" ", "");
        if (saved.trace() != null) {
            scenario.attach(saved.trace().toAbsolutePath().toString(), "text/plain", "trace");
            attachToAllure(name, "application/zip", saved.trace(), "zip");
        }
        if (saved.video() != null) {
            scenario.attach(saved.video().toAbsolutePath().toString(), "text/plain", "video");
            attachToAllure(name, "video/webm", saved.video(), "webm");
        }
    }

    @After(order = 1)
    public void teardownData(Scenario scenario) {
        logger.info("Clearing after scenario data");
        UnitManagerApi unitManagerApi = ApiFactory.getApi(UnitManagerApi.class);
        EnergyProfileCalculatorApi epcApi = ApiFactory.getApi(EnergyProfileCalculatorApi.class);

        boolean isTelephoneInstructionsScenario =
                scenario.getSourceTagNames().contains("@TelephoneInstructions");
        if (session.data().has(Keys.ZONE_ID) && !isTelephoneInstructionsScenario) {
            try {
                long zoneId = session.data().get(Keys.ZONE_ID);
                unitManagerApi.deleteZone(zoneId, true);
                logger.info("{} zone deleted successfully", zoneId);
                try {
                    epcApi.deleteZoneSummaries(Set.of(zoneId));
                } catch (AssertionError e) {
                    logger.info("Epc delete zone summary endpoint was not available: {}", e.getMessage());
                }
            } catch (Exception | AssertionError e) {
                logger.info("No zones found to delete");
            }
        }
        long testEndTime = System.currentTimeMillis();
        float duration = (float) ((testEndTime - featureStartTime) / 1000.0);
        testResultUpdated(scenario, duration);
    }

    public void testResultUpdated(Scenario scenario, float duration) {
        LinkedHashMap<String, Object> testResult = new LinkedHashMap<String, Object>();
        String testId = "";
        testId = scenario.getName();
        testResult.put("testCaseResult", scenario.getStatus());
        testResult.put("testCaseName", scenario.getName());
        if (!testId.isEmpty()) {
            testResult.put("testCaseId", testId);
        } else {
            testResult.put("testCaseId", "");
        }
        TestExecutionResult testExecutionResult = new TestExecutionResult();
        testExecutionResult.setTestId(testId);
        testExecutionResult.setDuration(duration);
        if (scenario.isFailed()) {
            testExecutionResult.setStatus("Failed");
        } else {
            testExecutionResult.setStatus("Passed");
        }
        testExecutionMap.put(testId, testExecutionResult);
        logger.info("Test Result is {}", testResult);
    }

    private static void attachToAllure(String name, String type, Path file, String extension) {
        try {
            Allure.addAttachment(name, type, new ByteArrayInputStream(Files.readAllBytes(file)), extension);
        } catch (Exception ex) {
            logger.error("Exception encountered when attaching {}: {}", file, ex.getMessage());
        }
    }
}
