package com.agenticqa.steps;

import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.agenticqa.core.Variables;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.agenticqa.steps.Keys.ZONE_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Sets a unique zone id in one step and reads the same zone id in later steps of the scenario.
 * <p>
 * The id is kept in two places, so every kind of step can read it:
 * <ul>
 *   <li>the {@link TestSession} under {@link Keys#ZONE_ID}, as a {@code long}, for Java step code in any step
 *       class: {@code long zoneId = session.get(Keys.ZONE_ID);}</li>
 *   <li>the scenario variable {@code zoneId}, for feature files: {@code ${zoneId}} in request fields, paths,
 *       headers and expectations.</li>
 * </ul>
 * Both live per scenario (PicoContainer gives every step class of a scenario the same session and variables),
 * so parallel scenarios each have their own zone id and never see each other's.
 */
public class ZoneSteps {

    /** Name of the scenario variable that holds the zone id. */
    static final String VARIABLE = "zoneId";

    private final TestSession session;
    private final Variables vars;
    private Long readZoneId;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param vars    the scenario's variables
     */
    public ZoneSteps(TestSession session, Variables vars) {
        this.session = session;
        this.vars = vars;
    }

    /** Sets a new unique zone id for this scenario: {@code Given a unique zone id is set}. */
    @Given("a unique zone id is set")
    public void setZoneId() {
        long zoneId = TestData.uniqueNumber();
        session.put(ZONE_ID, zoneId);
        vars.put(VARIABLE, String.valueOf(zoneId));
        System.out.printf("[ZONE] [%s] zone id set: %d%n", Thread.currentThread().getName(), zoneId);
    }

    /**
     * Sets a given zone id, for example one an earlier response returned:
     * {@code Given the zone id is "${newZone}"}.
     *
     * @param value a whole number; may be a ${variable}
     */
    @Given("the zone id is {string}")
    public void setZoneId(String value) {
        long zoneId = Long.parseLong(vars.resolve(value).trim());
        session.put(ZONE_ID, zoneId);
        vars.put(VARIABLE, String.valueOf(zoneId));
    }

    /** Reads the zone id an earlier step set: {@code When I read the zone id}. */
    @When("I read the zone id")
    public void readZoneId() {
        readZoneId = session.get(ZONE_ID); // fails with a clear message if no earlier step set one
        System.out.printf("[ZONE] [%s] zone id read: %d%n", Thread.currentThread().getName(), readZoneId);
    }

    /** Checks that the zone id read is the one that was set, in the session and in ${zoneId}. */
    @Then("the zone id read is the one that was set")
    public void sameZoneId() {
        assertTrue(readZoneId != null, "No zone id was read. Add the step: When I read the zone id");
        assertEquals(readZoneId, session.get(ZONE_ID), "zone id in the session");
        assertEquals(String.valueOf(readZoneId), vars.get(VARIABLE), "zone id in ${" + VARIABLE + "}");
    }

    /**
     * Checks the zone id against an expected value: {@code Then the zone id should be "${expectedZone}"}.
     *
     * @param expected the expected number; may be a ${variable}
     */
    @Then("the zone id should be {string}")
    public void zoneIdIs(String expected) {
        assertEquals(session.get(ZONE_ID).longValue(), Long.parseLong(vars.resolve(expected).trim()), "zone id");
    }

    /**
     * Copies the current zone id into another variable, for example to compare it with a later one.
     *
     * @param variable the variable name
     */
    @Given("I save the zone id as {string}")
    public void saveZoneId(String variable) {
        vars.put(variable, String.valueOf(session.get(ZONE_ID)));
    }

    /**
     * Checks that the zone id differs from a value: {@code Then the zone id is not "${firstZone}"}.
     *
     * @param other the value it must not equal; may be a ${variable}
     */
    @Then("the zone id is not {string}")
    public void zoneIdIsNot(String other) {
        assertNotEquals(session.get(ZONE_ID).longValue(), Long.parseLong(vars.resolve(other).trim()), "zone id");
    }

    /**
     * Saves a new unique number under any variable name, for other ids than the zone id:
     * {@code Given a unique number is saved as "orderNo"}, then use {@code ${orderNo}}.
     *
     * @param variable the variable name
     */
    @Given("a unique number is saved as {string}")
    public void uniqueNumber(String variable) {
        vars.put(variable, String.valueOf(TestData.uniqueNumber()));
    }
}
