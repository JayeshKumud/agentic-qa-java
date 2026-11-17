package com.agenticqa.runner;

import io.cucumber.testng.Pickle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PLACEHOLDER for the company FeatureAwareManager: decides from feature flags which scenarios run when
 * {@code Config.isFeatureAware()} is true. This version runs every scenario. Paste the real implementation here.
 */
public class FeatureAwareManager {
    private static final Logger logger = LoggerFactory.getLogger(FeatureAwareManager.class);

    /** Loads the feature flags. Placeholder: loads nothing. */
    public void initializeFeatureSet() {
        logger.warn("FeatureAwareManager is a placeholder: no feature flags are loaded, every scenario runs");
    }

    /**
     * Tells whether a scenario runs with the loaded feature flags.
     *
     * @param pickle the scenario
     * @return always true in this placeholder
     */
    public boolean shouldRunTest(Pickle pickle) {
        return true;
    }
}
