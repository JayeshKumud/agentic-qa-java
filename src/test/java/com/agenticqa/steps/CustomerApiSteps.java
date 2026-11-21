package com.agenticqa.steps;

import com.agenticqa.api.ApiFactory;
import com.agenticqa.apis.CustomerApi;
import com.agenticqa.apis.model.Customer;
import com.agenticqa.core.UniqueData;
import com.agenticqa.core.TestSession;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.RESPONSE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Steps for the customers REST API, through {@link CustomerApi} from {@link ApiFactory}. Each step stores what it
 * created or changed as "the customer" in the scenario's {@link TestSession}, so GUI steps of the same scenario
 * work on it too. Names are made unique per scenario (a random token is appended), so scenarios can run in
 * parallel against the same app; every customer created is deleted afterwards.
 */
public class CustomerApiSteps {
    private static final Logger logger = LoggerFactory.getLogger(CustomerApiSteps.class);

    private final TestSession session;
    private final ServiceCleanup cleanup;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param cleanup deletes what the scenario created, after it
     */
    public CustomerApiSteps(TestSession session, ServiceCleanup cleanup) {
        this.session = session;
        this.cleanup = cleanup;
    }

    /**
     * Creates a customer through the API and remembers it as "the customer".
     *
     * @param name the readable name; a unique token is appended and the email is derived from it
     */
    @Given("a customer named {string} exists")
    @When("I create a customer named {string}")
    public void createCustomer(String name) {
        String unique = UniqueData.uniqueName(name);
        Response response = customerApi().postCustomer(new Customer(unique, UniqueData.emailFor(unique)));
        session.data().put(RESPONSE, response);
        Customer customer = response.as(Customer.class);
        deleteAfterScenario(cleanup, customer.getId());
        session.data().put(CUSTOMER, customer);
        logger.info("Created {}", customer);
    }

    /**
     * Changes the status of the customer.
     *
     * @param status the new status
     */
    @When("I change the customer status to {string}")
    public void changeStatus(String status) {
        Customer current = session.data().get(CUSTOMER);
        Customer changed = new Customer(null, current.getName(), current.getEmail(), status);
        Response response = customerApi().putCustomer(current.getId(), changed);
        session.data().put(RESPONSE, response);
        session.data().put(CUSTOMER, response.as(Customer.class));
        logger.info("Changed customer {} status to {}", current.getId(), status);
    }

    /** Deletes the customer. */
    @When("I delete the customer")
    public void deleteCustomer() {
        Customer current = session.data().get(CUSTOMER);
        session.data().put(RESPONSE, customerApi().deleteCustomer(current.getId()));
        cleanup.cancel(cleanupKey(current.getId()));
    }

    /**
     * Checks that GET /customers/{id} returns the customer with the expected status.
     *
     * @param status the expected status
     */
    @Then("the customer can be found by id with status {string}")
    public void findById(String status) {
        Customer expected = session.data().get(CUSTOMER);
        Customer actual = customerApi().getCustomer(expected.getId()).as(Customer.class);
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getName(), expected.getName());
        assertEquals(actual.getEmail(), expected.getEmail());
        assertEquals(actual.getStatus(), status);
    }

    /** Checks that the customer appears in GET /customers. */
    @Then("the customer appears in the customer list")
    public void appearsInList() {
        assertTrue(listedIds().contains(session.data().get(CUSTOMER).getId()), "Customer missing from list");
    }

    /** Checks that the customer no longer appears in GET /customers. */
    @Then("the customer no longer appears in the customer list")
    public void absentFromList() {
        assertFalse(listedIds().contains(session.data().get(CUSTOMER).getId()), "Deleted customer still listed");
    }

    /**
     * Checks the status code of the last API call.
     *
     * @param status the expected HTTP status
     */
    @Then("the API responds with status {int}")
    public void responseStatus(int status) {
        Response response = session.data().get(RESPONSE);
        assertEquals(response.getStatusCode(), status, response.getBody().asString());
    }

    /**
     * Registers a customer for deletion after the scenario. Used by the GUI steps too, for customers added
     * through the page.
     *
     * @param cleanup the scenario's cleanup
     * @param id      the customer id
     */
    static void deleteAfterScenario(ServiceCleanup cleanup, long id) {
        cleanup.add(cleanupKey(id), () -> ApiFactory.getApi(CustomerApi.class).deleteCustomer(id));
    }

    private static String cleanupKey(long id) {
        return "customer " + id;
    }

    /** @return the ids in GET /customers */
    private List<Long> listedIds() {
        return customerApi().getCustomers().jsonPath().getList("id", Long.class);
    }

    private CustomerApi customerApi() {
        return ApiFactory.getApi(CustomerApi.class);
    }
}
