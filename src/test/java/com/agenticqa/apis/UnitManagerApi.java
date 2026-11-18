package com.agenticqa.apis;

import com.agenticqa.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PLACEHOLDER for the company UnitManagerApi, used by {@code TestInitializeHooks.teardownData} to delete the zone
 * a scenario created. This version calls no service and deletes nothing. Paste the real API class here (its
 * base URL is {@code Settings.getBaseUrl()}).
 */
public class UnitManagerApi implements Api {
    private static final Logger logger = LoggerFactory.getLogger(UnitManagerApi.class);

    /**
     * Deletes a zone. Placeholder: deletes nothing.
     *
     * @param zoneId the zone id
     * @param force  delete even if the zone is in use
     */
    public void deleteZone(long zoneId, boolean force) {
        logger.warn("UnitManagerApi is a placeholder: zone {} was not deleted", zoneId);
    }
}
