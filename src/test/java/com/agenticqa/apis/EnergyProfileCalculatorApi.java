package com.agenticqa.apis;

import com.agenticqa.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * PLACEHOLDER for the company EnergyProfileCalculatorApi, used by {@code TestInitializeHooks.teardownData}. This
 * version calls no service and deletes nothing. Paste the real API class here.
 */
public class EnergyProfileCalculatorApi implements Api {
    private static final Logger logger = LoggerFactory.getLogger(EnergyProfileCalculatorApi.class);

    /**
     * Deletes the summaries of zones. Placeholder: deletes nothing.
     *
     * @param zoneIds the zone ids
     */
    public void deleteZoneSummaries(Set<Long> zoneIds) {
        logger.warn("EnergyProfileCalculatorApi is a placeholder: summaries of zones {} were not deleted", zoneIds);
    }
}
