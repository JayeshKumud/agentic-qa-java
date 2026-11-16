package com.agenticqa.steps;

import com.agenticqa.apis.model.Unique;
import com.agenticqa.core.TestSession;
import com.agenticqa.helpers.BackUniqueTransit;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static com.agenticqa.steps.Keys.BASE_UNIQUE;
import static org.testng.Assert.assertEquals;

/**
 * Steps for uniques: one step stores a unique by its base name in the scenario's session, and a later step
 * builds a {@link BackUniqueTransit}, which swaps the base names for their registration ids.
 */
public class UniqueSteps {
    private static final Logger logger = LoggerFactory.getLogger(UniqueSteps.class);

    private final TestSession session;
    private BackUniqueTransit transit;

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public UniqueSteps(TestSession session) {
        this.session = session;
    }

    /**
     * Stores a unique: {@code Given the unique "U1" has registration id "REG-1001"}.
     *
     * @param baseUnique the base name later steps use
     * @param uniqRegId  its registration id
     */
    @Given("the unique {string} has registration id {string}")
    public void storeUnique(String baseUnique, String uniqRegId) {
        session.put(BASE_UNIQUE, baseUnique, new Unique(uniqRegId));
        logger.info("Stored unique {} with registration id {}", baseUnique, uniqRegId);
    }

    /**
     * Builds a back unique transit: {@code When I build a back unique transit at "10:00" for "U1, REG-77"}.
     *
     * @param time      the transit time
     * @param uniqueIds comma-separated base names or registration ids
     */
    @When("I build a back unique transit at {string} for {string}")
    public void buildTransit(String time, String uniqueIds) {
        transit = new BackUniqueTransit(time, uniqueIds);
    }

    /**
     * Checks the registration ids of the transit: {@code Then the transit registration ids are "REG-1001, REG-77"}.
     *
     * @param expected comma-separated registration ids, in order
     */
    @Then("the transit registration ids are {string}")
    public void transitRegIds(String expected) {
        assertEquals(transit.getUniqueRegIds(), Arrays.stream(expected.split(",")).map(String::trim).toList());
    }
}
