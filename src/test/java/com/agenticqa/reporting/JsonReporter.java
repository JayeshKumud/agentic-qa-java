package com.agenticqa.reporting;

import com.agenticqa.hooks.TestInitializeHooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Cucumber plugin that builds the test execution summary when the run finishes, from the results
 * {@link TestInitializeHooks} collected: totals and one entry per scenario (test id, status, duration). It writes it
 * to {@value #SUMMARY_FILE}. {@code TestRunner.afterSuite} adds the report URLs and prints it.
 * <p>
 * Register it in {@code @CucumberOptions(plugin = "com.agenticqa.reporting.JsonReporter")}.
 */
public class JsonReporter implements ConcurrentEventListener {
    private static final Logger logger = LoggerFactory.getLogger(JsonReporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Where the summary is written. */
    static final String SUMMARY_FILE = "target/reports/test-execution-summary.json";

    /** The summary of the run, filled when the run finishes; empty before that. */
    public static final ObjectNode testExecutionSummaryJO = MAPPER.createObjectNode();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunFinished.class, event -> testRunFinished());
    }

    private void testRunFinished() {
        List<TestExecutionResult> results;
        synchronized (TestInitializeHooks.testExecutionMap) {
            results = new ArrayList<>(TestInitializeHooks.testExecutionMap.values());
        }
        ArrayNode rows = MAPPER.createArrayNode();
        results.forEach(result -> rows.add(MAPPER.valueToTree(result)));
        testExecutionSummaryJO.put("total", results.size());
        testExecutionSummaryJO.put("passed", results.stream().filter(r -> "Passed".equals(r.getStatus())).count());
        testExecutionSummaryJO.put("failed", results.stream().filter(r -> "Failed".equals(r.getStatus())).count());
        testExecutionSummaryJO.set("testResults", rows);

        Path file = Paths.get(SUMMARY_FILE);
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), testExecutionSummaryJO);
        } catch (IOException e) {
            logger.error("Could not write {}: {}", file, e.getMessage());
        }
    }
}
