package com.agenticqa.apis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A unique created during a scenario, stored by its base name in the scenario's {@code TestSession} under
 * {@code Keys.BASE_UNIQUE}, so later steps (for example {@code BackUniqueTransit}) can swap the name for its
 * registration id.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Unique {

    private String uniqRegId;

    /** For Jackson. */
    public Unique() {}

    /**
     * @param uniqRegId the registration id the service gave the unique
     */
    public Unique(String uniqRegId) {
        this.uniqRegId = uniqRegId;
    }

    public String getUniqRegId() {
        return uniqRegId;
    }

    public void setUniqRegId(String uniqRegId) {
        this.uniqRegId = uniqRegId;
    }

    @Override
    public String toString() {
        return "Unique[uniqRegId=" + uniqRegId + "]";
    }
}
