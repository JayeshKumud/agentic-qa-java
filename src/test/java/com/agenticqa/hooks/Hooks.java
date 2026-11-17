package com.agenticqa.hooks;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.agenticqa.reporting.TestExecutionResult;
import com.agenticqa.reporting.TestExecutionResults;
import com.agenticqa.steps.Keys;
import com.agenticqa.ui.healing.HealEvent;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.Status;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standard pre and post actions that run around every scenario and step, so no feature file or step has to repeat
 * them. See docs/hooks.md for the order.
 * <ul>
 *   <li>Before: binds the session, logs the tags, skips @mute scenarios and checks the test-case tag.</li>
 *   <li>After every step: a screenshot of a browser scenario ({@code ui.screenshots: everyStep}).</li>
 *   <li>After: healed locators, failure screenshot and debug information, closes the browser context (trace and
 *       video of failed scenarios are kept and attached to Allure) and records the scenario's result for the test
 *       execution summary. Test data is deleted before this, by {@link com.agenticqa.steps.ServiceCleanup}.</li>
 * </ul>
 */
public class Hooks {
    private static final Logger logger = LoggerFactory.getLogger(Hooks.class);

    /** Longest response body attached as debug information. */
    private static final int MAX_BODY = 4000;

    private final TestSession session;
    private long startTime = System.currentTimeMillis();
    private String testCaseTag;

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public Hooks(TestSession session) {
        this.session = session;
    }

    /**
     * Runs before each scenario, as the first {@code @Before} hook (lowest order runs first), so every later hook
     * and step can use {@link TestSession#current()}. Also starts the scenario's clock.
     *
     * @param scenario the running Cucumber scenario
     */
    @Before(order = 0)
    public void before(Scenario scenario) {
        startTime = System.currentTimeMillis();
        session.scenarioName(scenario.getName());
        session.bindToCurrentThread(); // TestSession.current() now returns this scenario's session
        logger.info("Scenario '{}', tags: {}", scenario.getName(), scenario.getSourceTagNames());
    }

    /**
     * Skips muted scenarios and checks the test-case tag ({@code testCaseTag} in config/common.yaml).
     *
     * @param scenario the running Cucumber scenario
     * @throws SkipException  if the scenario is tagged @mute; Cucumber and TestNG report it as skipped
     * @throws AssertionError if the test-case tag is missing or invalid and it is required
     */
    @Before(order = 1)
    public void checkTags(Scenario scenario) {
        List<String> tags = new ArrayList<>(scenario.getSourceTagNames());
        Settings.TestCaseTag rule = session.settings().testCaseTag();
        testCaseTag = TestCaseTag.find(tags, rule.prefix()); // so a muted scenario is reported under its id too
        if (TestCaseTag.isMuted(tags)) {
            logger.info("This test is muted. Hence, skipping this test: {}", scenario.getName());
            throw new SkipException("Skipping this test: it is tagged @mute");
        }
        try {
            TestCaseTag.validate(tags, rule.prefix());
        } catch (AssertionError e) {
            if (rule.required()) {
                logger.error("{} ({})", e.getMessage(), scenario.getName());
                throw e;
            }
            logger.warn("{} ({}); continuing because testCaseTag.required is false", e.getMessage(), scenario.getName());
        }
    }

    /**
     * Attaches a screenshot after every step when {@code ui.screenshots} is everyStep. Only scenarios that opened
     * a page get one, so API scenarios never start a browser for it.
     *
     * @param scenario the running Cucumber scenario
     */
    @AfterStep
    public void afterStep(Scenario scenario) {
        if (!session.settings().ui().screenshotsEveryStep() || !session.hasPage()) return;
        try {
            scenario.attach(session.page().screenshot(), "image/png", "step-screenshot");
        } catch (RuntimeException e) {
            logger.warn("Failed to take a step screenshot: {}", e.getMessage()); // must not hide the real result
        }
    }

    /**
     * Runs after each scenario. Healed locators are attached to the report. If it failed, a full-page screenshot
     * (if a page was used) and debug information are attached.
     * Then it always closes everything; for failed scenarios the trace zip and the video (ui.video) are kept,
     * their paths attached and the files themselves attached to Allure. Last, the result is recorded for the test
     * execution summary.
     *
     * @param scenario the finished Cucumber scenario
     */
    @After
    public void after(Scenario scenario) {
        List<HealEvent> heals = session.healEvents();
        if (!heals.isEmpty()) {
            String text = heals.stream().map(HealEvent::summary).collect(Collectors.joining(System.lineSeparator()));
            scenario.attach("Locators healed - update the locator file (src/main/resources/locators):"
                    + System.lineSeparator() + text, "text/plain", "self-healing");
        }
        boolean failed = scenario.isFailed();
        if (failed && session.hasPage()) {
            try {
                byte[] png = session.page().screenshot(new Page.ScreenshotOptions().setFullPage(true));
                scenario.attach(png, "image/png", "failure-screenshot");
            } catch (RuntimeException ignored) {
                // a missing screenshot must not hide the real failure
            }
        }
        if (failed) attachDebugInformation(scenario);

        TestSession.SavedFiles saved = session.close(failed);
        if (saved.trace() != null) {
            scenario.attach(saved.trace().toAbsolutePath().toString(), "text/plain", "trace");
            attachToAllure(scenario.getName() + " - trace", "application/zip", saved.trace(), "zip");
        }
        if (saved.video() != null) {
            scenario.attach(saved.video().toAbsolutePath().toString(), "text/plain", "video");
            attachToAllure(scenario.getName() + " - video", "video/webm", saved.video(), "webm");
        }
        recordResult(scenario);
    }

    /**
     * Attaches what helps to understand a failure: the page's URL and title, and the last API response. Every
     * part is best effort.
     *
     * @param scenario the failed scenario
     */
    private void attachDebugInformation(Scenario scenario) {
        StringBuilder info = new StringBuilder();
        if (session.hasPage()) {
            try {
                Page page = session.page();
                info.append("Page URL: ").append(page.url()).append(System.lineSeparator())
                        .append("Page title: ").append(page.title()).append(System.lineSeparator());
            } catch (RuntimeException e) {
                info.append("Page: not available (").append(e.getMessage()).append(')').append(System.lineSeparator());
            }
        }
        if (session.has(Keys.RESPONSE)) {
            try {
                Response response = session.get(Keys.RESPONSE);
                String body = response.asString();
                if (body.length() > MAX_BODY) body = body.substring(0, MAX_BODY) + "... (cut)";
                info.append("Last API response: ").append(response.getStatusLine()).append(System.lineSeparator())
                        .append(body).append(System.lineSeparator());
            } catch (RuntimeException e) {
                info.append("Last API response: not readable (").append(e.getMessage()).append(')');
            }
        }
        if (!info.isEmpty()) scenario.attach(info.toString(), "text/plain", "debug-info");
    }

    /**
     * Attaches a kept file (trace or video) to the Allure report, so it can be downloaded from there.
     *
     * @param name      the attachment name
     * @param type      its media type
     * @param file      the file
     * @param extension the file extension, without dot
     */
    private static void attachToAllure(String name, String type, Path file, String extension) {
        try {
            Allure.addAttachment(name, type, new ByteArrayInputStream(Files.readAllBytes(file)), extension);
        } catch (IOException | RuntimeException e) {
            logger.error("Could not attach {} to Allure: {}", file, e.getMessage());
        }
    }

    /**
     * Records the scenario's result for the test execution summary: its test-case tag (or its name if it has
     * none), name, status and duration.
     *
     * @param scenario the finished scenario
     */
    private void recordResult(Scenario scenario) {
        float duration = (System.currentTimeMillis() - startTime) / 1000f;
        String status = scenario.isFailed() ? "Failed"
                : scenario.getStatus() == Status.SKIPPED ? "Skipped" : "Passed";
        String id = testCaseTag != null ? testCaseTag : scenario.getName();
        TestExecutionResult result = new TestExecutionResult(id, scenario.getName(), status, duration);
        TestExecutionResults.record(scenario.getUri() + ":" + scenario.getLine(), result);
        logger.info("Test result: {}", result);
    }
}
