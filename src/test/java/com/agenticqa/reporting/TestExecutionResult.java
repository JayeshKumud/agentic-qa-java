package com.agenticqa.reporting;

/**
 * The result of one scenario, as written to the test execution summary.
 *
 * @param testCaseId      the test-case tag, for example @PRKLM-1001, or the scenario name if it has none
 * @param testCaseName    the scenario name
 * @param status          Passed, Failed or Skipped
 * @param durationSeconds how long the scenario took, from its first hook to its last
 */
public record TestExecutionResult(String testCaseId, String testCaseName, String status, float durationSeconds) {}
