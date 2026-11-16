package com.agenticqa.steps;

import com.agenticqa.api.ApiClient;
import com.agenticqa.api.ApiState;
import com.agenticqa.api.ApiState.Reply;
import com.agenticqa.api.Customer;
import com.agenticqa.api.EndpointClient;
import com.agenticqa.api.model.CustomerRequest;
import com.agenticqa.core.TestSession;
import com.agenticqa.core.Variables;
import com.microsoft.playwright.APIResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.agenticqa.steps.Keys.CUSTOMER_REQUEST;
import static com.agenticqa.steps.Keys.RESPONSE;
import static org.testng.Assert.assertEquals;

/**
 * Customer requests built as a POJO ({@link CustomerRequest}) instead of a JSON template. The POJO is kept in the
 * session under {@link Keys#CUSTOMER_REQUEST}, changed with its typed setters, and sent with
 * {@link EndpointClient}, which serializes it with Jackson. The response is stored under a name like every other
 * response, so the generic {@link HttpSteps} check it ({@code Then the "pojo" response status is 201}).
 */
public class CustomerPojoSteps {

    private final TestSession session;
    private final ApiState api;
    private final Variables vars;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param api     the scenario's named requests and responses
     * @param vars    the scenario's variables
     */
    public CustomerPojoSteps(TestSession session, ApiState api, Variables vars) {
        this.session = session;
        this.api = api;
        this.vars = vars;
    }

    /**
     * Builds the request POJO from a field/value table; {@code {unique}} and {@code ${var}} are filled in:
     * <pre>
     * Given a customer request object:
     *   | name   | Ada Lovelace {unique}              |
     *   | email  | ada.lovelace.{unique}@example.test |
     * </pre>
     *
     * @param table name, email and/or status rows
     */
    @Given("a customer request object:")
    public void build(DataTable table) {
        Map<String, String> rows = table.asMap(String.class, String.class);
        CustomerRequest request = new CustomerRequest();
        rows.forEach((field, value) -> set(request, field.trim(), vars.resolve(value)));
        session.put(CUSTOMER_REQUEST, request);
    }

    /**
     * Changes one field of the request POJO through its setter.
     *
     * @param field name, email or status
     * @param value the new value
     */
    @Given("the customer request object {word} is {string}")
    public void change(String field, String value) {
        set(session.get(CUSTOMER_REQUEST), field, vars.resolve(value));
    }

    /**
     * Sends the request POJO to a catalog endpoint: {@code When I call "customers.create" with the customer
     * request object as "pojo"}. Path parameters such as {id} come from variables of the same name.
     *
     * @param endpoint GROUP.KEY
     * @param name     the name to store the response under
     */
    @When("I call {string} with the customer request object as {string}")
    public void send(String endpoint, String name) {
        CustomerRequest body = session.get(CUSTOMER_REQUEST);
        APIResponse response = new EndpointClient(session).call(endpoint, Map.of(), body, Map.of(), vars::find);
        session.put(RESPONSE, response);
        api.store(name, Reply.of(response));
    }

    /**
     * Reads a response back into a typed object and compares it with the request POJO that was sent.
     *
     * @param name the response name
     */
    @Then("the {string} response matches the customer request object")
    public void responseMatches(String name) {
        CustomerRequest sent = session.get(CUSTOMER_REQUEST);
        Customer received = ApiClient.read(api.reply(name).body(), Customer.class);
        assertEquals(received.name(), sent.getName(), "name");
        assertEquals(received.email(), sent.getEmail(), "email");
        if (sent.getStatus() != null) assertEquals(received.status(), sent.getStatus(), "status");
    }

    private static void set(CustomerRequest request, String field, String value) {
        switch (field.toLowerCase()) {
            case "name" -> request.setName(value);
            case "email" -> request.setEmail(value);
            case "status" -> request.setStatus(value);
            default -> throw new IllegalArgumentException("CustomerRequest has no field '" + field
                    + "'. Fields: name, email, status");
        }
    }
}
