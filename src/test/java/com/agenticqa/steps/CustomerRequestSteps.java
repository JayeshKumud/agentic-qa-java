package com.agenticqa.steps;

import com.agenticqa.api.ApiClient;
import com.agenticqa.api.ApiState;
import com.agenticqa.api.ApiState.Reply;
import com.agenticqa.api.Customer;
import com.agenticqa.api.CustomerApi;
import com.agenticqa.core.JsonPaths;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.agenticqa.core.Variables;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.RESPONSE;

/**
 * Customer-specific request steps, plus the bridge between the generic {@link HttpSteps} and the customer steps
 * of the API and the GUI.
 * <p>
 * The customer request is the named request {@value #REQUEST} of {@link ApiState}, so the generic steps can
 * change it too ({@code the "customer" request field "status" is set to ...}) and its response can be read as
 * {@code the "customer" response}. Field names are paths (nested fields, {@code json:} values) as in HttpSteps.
 * Sending it stores the API's answer under {@link Keys#CUSTOMER} and {@link Keys#RESPONSE}, the keys the other
 * API and UI steps use, so a later browser step works on the customer the request created.
 */
public class CustomerRequestSteps {

    /** Name of the customer request and its response in {@link ApiState}. */
    static final String REQUEST = "customer";

    private final TestSession session;
    private final ApiState api;
    private final Variables vars;
    private final CustomerApi customers;
    private final CustomerCleanup cleanup;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param api     the scenario's named requests and responses
     * @param vars    the scenario's variables
     * @param cleanup the per-scenario cleanup tracker
     */
    public CustomerRequestSteps(TestSession session, ApiState api, Variables vars, CustomerCleanup cleanup) {
        this.session = session;
        this.api = api;
        this.vars = vars;
        this.customers = new CustomerApi(session);
        this.cleanup = cleanup;
    }

    /**
     * Starts a new customer request from a two-column table of field and value:
     * <pre>
     *   | name  | Mary Jackson {unique}              |
     *   | email | mary.jackson.{unique}@example.test |
     * </pre>
     * Fields not given are not sent (status then gets the server default, "active").
     *
     * @param table field/value rows
     */
    @Given("a new customer request:")
    public void newRequest(DataTable table) {
        api.start(REQUEST, "{}");
        updateFields(table);
    }

    /**
     * Starts a new customer request from a JSON file under src/test/resources/testdata.
     *
     * @param path for example "customers/new_customer.json"
     */
    @Given("a new customer request from {string}")
    public void newRequestFromFile(String path) {
        api.start(REQUEST, vars.resolve(TestData.file(path)));
    }

    /**
     * Starts a new customer request from a raw JSON doc string, sent exactly as written (after placeholders are
     * filled in), so it may hold wrong types, extra fields or even be malformed.
     *
     * @param body the request body
     */
    @Given("the customer request body is:")
    public void rawBody(String body) {
        api.start(REQUEST, vars.resolve(body));
    }

    /**
     * Changes or adds one field of the customer request.
     *
     * @param field the field path
     * @param value the new value; text unless prefixed with json:
     */
    @Given("the request field {string} is set to {string}")
    public void setField(String field, String value) {
        var resolved = JsonPaths.value(vars.resolve(value));
        api.edit(REQUEST, body -> JsonPaths.set(body, field.trim(), resolved));
    }

    /**
     * Changes several fields of the customer request; the others keep their values.
     *
     * @param table field/value rows
     */
    @Given("the request fields are updated:")
    public void updateFields(DataTable table) {
        Map<String, String> rows = table.asMap(String.class, String.class);
        api.edit(REQUEST, body -> rows.forEach((field, value) ->
                JsonPaths.set(body, field.trim(), JsonPaths.value(vars.resolve(value)))));
    }

    /**
     * Removes a field, so it is not sent at all (for example to test a missing required field).
     *
     * @param field the field path
     */
    @Given("the request field {string} is removed")
    public void removeField(String field) {
        api.edit(REQUEST, body -> JsonPaths.remove(body, field.trim()));
    }

    /** POST /customers with the request; on success the created customer becomes "the customer". */
    @When("I send the create customer request")
    public void sendCreate() {
        APIResponse response = customers.create(api.request(REQUEST).body());
        remember(response);
        if (response.status() == 201) {
            Customer created = CustomerApi.asCustomer(response);
            cleanup.track(created.id());
            session.put(CUSTOMER, created);
            // later edits start from what the server stored, including defaults such as status
            ObjectNode stored = (ObjectNode) JsonPaths.parse(response.text());
            stored.remove("id");
            api.start(REQUEST, JsonPaths.write(stored));
        }
    }

    /** PUT /customers/{id} of "the customer" with the request; on success the answer becomes "the customer". */
    @When("I send the update request for the customer")
    public void sendUpdate() {
        APIResponse response = customers.update(session.get(CUSTOMER).id(), api.request(REQUEST).body());
        remember(response);
        if (response.status() == 200) {
            session.put(CUSTOMER, CustomerApi.asCustomer(response));
        }
    }

    /**
     * Bridge from the generic steps: a customer created or read with {@link HttpSteps} becomes "the customer",
     * so the customer API and GUI steps (search the page, compare rows, ...) work on it. It is also deleted
     * after the scenario.
     *
     * @param response the name of a response whose body is one customer
     */
    @Given("the {string} response is the customer")
    public void responseIsTheCustomer(String response) {
        Customer customer = ApiClient.read(api.reply(response).body(), Customer.class);
        cleanup.track(customer.id());
        session.put(CUSTOMER, customer);
    }

    /**
     * Bridge the other way: a value of "the customer" (for example one added through the GUI) becomes a
     * variable for the generic steps, such as {@code "/customers/${customerId}"}.
     *
     * @param field    id, name, email or status
     * @param variable the variable name
     */
    @Given("I save the customer's {word} as {string}")
    public void saveCustomerField(String field, String variable) {
        Customer customer = session.get(CUSTOMER);
        String value = switch (field.toLowerCase()) {
            case "id" -> String.valueOf(customer.id());
            case "name" -> customer.name();
            case "email" -> customer.email();
            case "status" -> customer.status();
            default -> throw new IllegalArgumentException("Unknown customer field '" + field
                    + "'. Use id, name, email or status.");
        };
        vars.put(variable, value);
    }

    private void remember(APIResponse response) {
        session.put(RESPONSE, response);
        api.store(REQUEST, Reply.of(response));
    }
}
