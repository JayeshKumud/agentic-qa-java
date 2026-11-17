package com.agenticqa.reporting;

/** The result of one scenario, collected by {@code TestInitializeHooks} and written by {@link JsonReporter}. */
public class TestExecutionResult {
    private String testId;
    private float duration;
    private String status;

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    /** @return duration in seconds */
    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    /** @return Passed or Failed */
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TestExecutionResult{testId='" + testId + "', duration=" + duration + ", status='" + status + "'}";
    }
}
