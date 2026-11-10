package com.agenticqa.hooks;

import com.agenticqa.api.ApiCall;
import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.HealEvent;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standard pre and post actions that run around every scenario, so no feature file or step has to repeat them.
 */
public class Hooks {

    private final TestSession session;

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public Hooks(TestSession session) {
        this.session = session;
    }

    /**
     * Runs before each scenario.
     *
     * @param scenario the running Cucumber scenario
     */
    @Before
    public void before(Scenario scenario) {
        session.scenarioName(scenario.getName());
    }

    /**
     * Runs after each scenario. Healed locators are attached to the report. If it failed, the log of its HTTP
     * calls is attached, and if a page was used, a full-page screenshot too.
     * Then it always closes everything; for failed scenarios the trace zip is kept and its path attached.
     *
     * @param scenario the finished Cucumber scenario
     */
    @After
    public void after(Scenario scenario) {
        List<HealEvent> heals = session.healEvents();
        if (!heals.isEmpty()) {
            String text = heals.stream().map(HealEvent::summary).collect(Collectors.joining(System.lineSeparator()));
            scenario.attach("Locators healed - update the page object:" + System.lineSeparator() + text,
                    "text/plain", "self-healing");
        }
        boolean failed = scenario.isFailed();
        List<ApiCall> calls = session.apiCalls();
        if (failed && !calls.isEmpty()) {
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < calls.size(); i++) {
                log.append(i + 1).append(". ").append(calls.get(i).describe()).append(System.lineSeparator());
            }
            scenario.attach(log.toString(), "text/plain", "api-calls");
        }
        if (failed && session.hasPage()) {
            try {
                byte[] png = session.page().screenshot(new Page.ScreenshotOptions().setFullPage(true));
                scenario.attach(png, "image/png", "failure-screenshot");
            } catch (RuntimeException ignored) {
                // a missing screenshot must not hide the real failure
            }
        }
        Path trace = session.close(failed);
        if (trace != null) {
            scenario.attach(trace.toAbsolutePath().toString(), "text/plain", "trace");
        }
    }
}
