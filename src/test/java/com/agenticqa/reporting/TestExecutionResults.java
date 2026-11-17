package com.agenticqa.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The results of every scenario of the run, collected by {@code Hooks} after each scenario and written by
 * {@code TestRunner} after the suite. This is run-wide data, not scenario state, so it is one static concurrent
 * map: parallel scenarios record into it at the same time.
 * <p>
 * Results are keyed by the scenario's location (feature file and line), so a retried scenario keeps only its last
 * attempt and two rows of a Scenario Outline with the same test-case tag stay apart.
 */
public final class TestExecutionResults {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Map<String, TestExecutionResult> RESULTS = new ConcurrentHashMap<>();

    private TestExecutionResults() {}

    /**
     * Records the result of a scenario, replacing an earlier attempt of the same scenario.
     *
     * @param location the scenario's feature file and line, for example file:.../customers.feature:12
     * @param result   the result
     */
    public static void record(String location, TestExecutionResult result) {
        RESULTS.put(location, result);
    }

    /**
     * The results recorded so far, sorted by test case id.
     *
     * @return a copy
     */
    public static List<TestExecutionResult> all() {
        return RESULTS.values().stream()
                .sorted(Comparator.comparing(TestExecutionResult::testCaseId)
                        .thenComparing(TestExecutionResult::testCaseName))
                .toList();
    }

    /**
     * The summary of the run: totals and one row per scenario. {@code TestRunner} adds the report URLs.
     *
     * @return an ordered map, ready to be written as JSON
     */
    public static Map<String, Object> summary() {
        List<TestExecutionResult> all = all();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", all.size());
        summary.put("passed", count(all, "Passed"));
        summary.put("failed", count(all, "Failed"));
        summary.put("skipped", count(all, "Skipped"));
        summary.put("results", all);
        return summary;
    }

    /**
     * Writes a summary as indented JSON.
     *
     * @param summary the summary, usually {@link #summary()} plus report URLs
     * @param file    the file to write; its folder is created if needed
     */
    public static void write(Map<String, Object> summary, Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), summary);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /**
     * A summary as indented JSON text, for the console.
     *
     * @param summary the summary
     * @return the JSON
     */
    public static String toJson(Map<String, Object> summary) {
        try {
            return MAPPER.writeValueAsString(summary);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the summary as JSON", e);
        }
    }

    /** Forgets every result; for unit tests. */
    static void clear() {
        RESULTS.clear();
    }

    private static long count(List<TestExecutionResult> all, String status) {
        return all.stream().filter(r -> r.status().equals(status)).count();
    }
}
