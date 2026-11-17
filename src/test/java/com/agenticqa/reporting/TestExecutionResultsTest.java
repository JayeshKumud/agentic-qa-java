package com.agenticqa.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/** The per-scenario results behind test-execution-summary.json. */
public class TestExecutionResultsTest {

    @BeforeMethod
    @AfterMethod
    public void clear() {
        TestExecutionResults.clear();
    }

    @Test
    public void summaryCountsEachStatusAndSortsByTestCaseId() {
        TestExecutionResults.record("a.feature:3", new TestExecutionResult("@PRKLM-2", "second", "Failed", 1.5f));
        TestExecutionResults.record("a.feature:9", new TestExecutionResult("@PRKLM-1", "first", "Passed", 2f));
        TestExecutionResults.record("b.feature:4", new TestExecutionResult("@PRKLM-3", "third", "Skipped", 0f));

        Map<String, Object> summary = TestExecutionResults.summary();
        assertEquals(summary.get("total"), 3);
        assertEquals(summary.get("passed"), 1L);
        assertEquals(summary.get("failed"), 1L);
        assertEquals(summary.get("skipped"), 1L);
        assertEquals(TestExecutionResults.all().get(0).testCaseId(), "@PRKLM-1");
    }

    @Test
    public void aRetryReplacesTheEarlierAttemptOfTheSameScenario() {
        TestExecutionResults.record("a.feature:3", new TestExecutionResult("@PRKLM-1", "flaky", "Failed", 1f));
        TestExecutionResults.record("a.feature:3", new TestExecutionResult("@PRKLM-1", "flaky", "Passed", 1f));
        assertEquals(TestExecutionResults.all().size(), 1);
        assertEquals(TestExecutionResults.all().get(0).status(), "Passed");
    }

    @Test
    public void outlineRowsWithTheSameTagStayApart() {
        TestExecutionResults.record("a.feature:10", new TestExecutionResult("@PRKLM-5", "row", "Passed", 1f));
        TestExecutionResults.record("a.feature:11", new TestExecutionResult("@PRKLM-5", "row", "Passed", 1f));
        assertEquals(TestExecutionResults.all().size(), 2);
    }

    @Test
    public void writesTheSummaryWithExtraEntriesAsJson() throws Exception {
        TestExecutionResults.record("a.feature:3", new TestExecutionResult("@PRKLM-1", "first", "Passed", 2.25f));
        Map<String, Object> summary = TestExecutionResults.summary();
        summary.put("allure_report_url", "https://reports/allure");
        Path file = Files.createTempDirectory("summary").resolve("nested/test-execution-summary.json");

        TestExecutionResults.write(summary, file);

        JsonNode json = new ObjectMapper().readTree(file.toFile());
        assertEquals(json.get("total").asInt(), 1);
        assertEquals(json.get("allure_report_url").asText(), "https://reports/allure");
        JsonNode row = json.get("results").get(0);
        assertEquals(row.get("testCaseId").asText(), "@PRKLM-1");
        assertEquals(row.get("testCaseName").asText(), "first");
        assertEquals(row.get("status").asText(), "Passed");
        assertEquals(row.get("durationSeconds").asDouble(), 2.25);
    }
}
