package com.agenticqa.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PLACEHOLDER for the company AllurePublisher: publishes the Allure report in the pipeline
 * ({@code TestRunner.afterSuite}). This version publishes nothing. Paste the real implementation here.
 */
public final class AllurePublisher {
    private static final Logger logger = LoggerFactory.getLogger(AllurePublisher.class);

    /** URL of the published Allure report; set by {@link #publishAllureReports()}. */
    public static String ALLURE_REPORT_URL = "";

    private AllurePublisher() {}

    /** Publishes the Allure report. Placeholder: publishes nothing. */
    public static void publishAllureReports() {
        logger.warn("AllurePublisher is a placeholder: the Allure report was not published");
    }
}
