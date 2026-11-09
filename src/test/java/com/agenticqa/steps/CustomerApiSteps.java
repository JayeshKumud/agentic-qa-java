package com.agenticqa.steps;

import com.agenticqa.api.ApiClient;
import com.agenticqa.api.Customer;
import com.agenticqa.api.CustomerApi;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.RESPONSE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Steps for the customers REST API. Names in feature files are made unique per scenario (a random token is
 * appended), so scenarios can run in parallel against the same app; every customer created is deleted afterwards.
 */
public class CustomerApiSteps {

    private final TestSession session;
    private final CustomerApi customers;
    private final CustomerCleanup cleanup;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param cleanup the per-scenario cleanup tracker
     */
    public CustomerApiSteps(TestSession session, CustomerCleanup cleanup) {
        this.session = session;
        this.customers = new CustomerApi(session);
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
        String unique = TestData.uniqueName(name);
        APIResponse response = customers.create(Customer.newCustomer(unique, TestData.emailFor(unique)));
        session.put(RESPONSE, response);
        assertEquals(response.status(), 201, "Create failed: " + response.text());
        Customer customer = CustomerApi.asCustomer(response);
        cleanup.track(customer.id());
        session.put(CUSTOMER, customer);
    }

    /**
     * Changes the status of the customer.
     *
     * @param status the new status
     */
    @When("I change the customer status to {string}")
    public void changeStatus(String status) {
        Customer current = session.get(CUSTOMER);
        APIResponse response = customers.update(current.id(), current.withStatus(status));
        session.put(RESPONSE, response);
        assertEquals(response.status(), 200, "Update failed: " + response.text());
        session.put(CUSTOMER, CustomerApi.asCustomer(response));
    }

    /** Deletes the customer. */
    @When("I delete the customer")
    public void deleteCustomer() {
        Customer current = session.get(CUSTOMER);
        APIResponse response = customers.deleteById(current.id());
        session.put(RESPONSE, response);
        assertEquals(response.status(), 204, "Delete failed: " + response.text());
        cleanup.untrack(current.id());
    }

    /**
     * Checks that GET /customers/{id} returns the customer with the expected status.
     *
     * @param status the expected status
     */
    @Then("the customer can be found by id with status {string}")
    public void findById(String status) {
        Customer expected = session.get(CUSTOMER);
        APIResponse response = customers.getById(expected.id());
        assertEquals(response.status(), 200, "Get failed: " + response.text());
        Customer actual = CustomerApi.asCustomer(response);
        assertEquals(actual.id(), expected.id());
        assertEquals(actual.name(), expected.name());
        assertEquals(actual.email(), expected.email());
        assertEquals(actual.status(), status);
    }

    /** Checks that the customer appears in GET /customers. */
    @Then("the customer appears in the customer list")
    public void appearsInList() {
        assertTrue(listContains(session.get(CUSTOMER).id()), "Customer missing from list");
    }

    /** Checks that the customer no longer appears in GET /customers. */
    @Then("the customer no longer appears in the customer list")
    public void absentFromList() {
        assertFalse(listContains(session.get(CUSTOMER).id()), "Deleted customer still listed");
    }

    /**
     * Checks the status code of the last API call.
     *
     * @param status the expected HTTP status
     */
    @Then("the API responds with status {int}")
    public void responseStatus(int status) {
        APIResponse response = session.get(RESPONSE);
        assertEquals(response.status(), status, response.text());
    }

    /**
     * Tells whether GET /customers contains a customer id.
     *
     * @param id the id to look for
     * @return true if listed
     */
    private boolean listContains(long id) {
        APIResponse response = customers.list();
        assertEquals(response.status(), 200, "List failed: " + response.text());
        for (JsonNode node : ApiClient.json(response)) {
            if (node.path("id").asLong() == id) return true;
        }
        return false;
    }
}