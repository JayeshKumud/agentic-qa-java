package com.agenticqa.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PLACEHOLDER for the company ArtifactoryPublisher: uploads the reports to JFrog Artifactory in the pipeline
 * ({@code TestRunner.afterSuite}). This version uploads nothing. Paste the real implementation here.
 */
public final class ArtifactoryPublisher {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryPublisher.class);

    /** URL of the published report; set by {@link #publishArtifactoryReports()}. */
    public static String JFROG_ARTIFACTORY_REPORT_URL = "";

    private ArtifactoryPublisher() {}

    /** Uploads the reports. Placeholder: uploads nothing. */
    public static void publishArtifactoryReports() {
        logger.warn("ArtifactoryPublisher is a placeholder: no reports were uploaded");
    }
}
