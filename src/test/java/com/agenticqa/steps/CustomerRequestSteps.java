package com.agenticqa.steps;

import com.agenticqa.api.Customer;
import com.agenticqa.api.CustomerApi;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.microsoft.playwright.APIResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.REQUEST;
import static com.agenticqa.steps.Keys.RESPONSE;
import static com.agenticqa.steps.Keys.TOKEN;

/**
 * Steps that build a customer request in the feature file, change it in later steps, and then send it.
 * <p>
 * The request draft lives in the scenario's TestSession under {@link Keys#REQUEST}. Sending it stores the API's
 * answer under {@link Keys#CUSTOMER} and {@link Keys#RESPONSE}, the same keys the other API and UI steps use, so
 * any later step (for example a browser search) works on the customer the request created.
 * <p>
 * Values may contain {@code {unique}}: it is replaced by one random token per scenario, the same token in every
 * field, so names and emails stay related and parallel scenarios never collide.
 */
public class CustomerRequestSteps {

    private static final String UNIQUE = "{unique}";

    private final TestSession session;
    private final CustomerApi customers;
    private final CustomerCleanup cleanup;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param cleanup the per-scenario cleanup tracker
     */
    public CustomerRequestSteps(TestSession session, CustomerCleanup cleanup) {
        this.session = session;
        this.customers = new CustomerApi(session);
        this.cleanup = cleanup;
    }

    /**
     * Starts a new request from a two-column table of field and value:
     * <pre>
     *   | name  | Mary Jackson {unique}              |
     *   | email | mary.jackson.{unique}@example.test |
     * </pre>
     * Fields not given are left empty (status then gets the server default, "active").
     *
     * @param table field/value rows; fields are name, email and status
     */
    @Given("a new customer request:")
    public void newRequest(DataTable table) {
        Customer draft = new Customer(null, null, null, null);
        for (Map.Entry<String, String> row : table.asMap(String.class, String.class).entrySet()) {
            draft = withField(draft, row.getKey(), row.getValue());
        }
        session.put(REQUEST, draft);
    }

    /**
     * Changes one field of the request.
     *
     * @param field name, email or status
     * @param value the new value, may contain {unique}
     */
    @Given("the request field {string} is set to {string}")
    public void setField(String field, String value) {
        session.put(REQUEST, withField(session.get(REQUEST), field, value));
    }

    /**
     * Changes several fields of the request; the others keep their values.
     *
     * @param table field/value rows
     */
    @Given("the request fields are updated:")
    public void updateFields(DataTable table) {
        Customer draft = session.get(REQUEST);
        for (Map.Entry<String, String> row : table.asMap(String.class, String.class).entrySet()) {
            draft = withField(draft, row.getKey(), row.getValue());
        }
        session.put(REQUEST, draft);
    }

    /** POST /customers with the request; on success the created customer becomes "the customer". */
    @When("I send the create customer request")
    public void sendCreate() {
        APIResponse response = customers.create(session.get(REQUEST));
        session.put(RESPONSE, response);
        if (response.status() == 201) {
            Customer created = CustomerApi.asCustomer(response);
            cleanup.track(created.id());
            session.put(CUSTOMER, created);
            // later edits start from what the server stored, including defaults such as status
            session.put(REQUEST, created.withId(null));
        }
    }

    /** PUT /customers/{id} of "the customer" with the request; on success the answer becomes "the customer". */
    @When("I send the update request for the customer")
    public void sendUpdate() {
        APIResponse response = customers.update(session.get(CUSTOMER).id(), session.get(REQUEST));
        session.put(RESPONSE, response);
        if (response.status() == 200) {
            session.put(CUSTOMER, CustomerApi.asCustomer(response));
        }
    }

    /**
     * Copy of a request with one field changed.
     *
     * @param draft the current request
     * @param field name, email or status (case-insensitive)
     * @param raw   the value from the feature file, {unique} not yet replaced
     * @return the changed copy
     * @throws IllegalArgumentException for an unknown field
     */
    private Customer withField(Customer draft, String field, String raw) {
        String value = raw.replace(UNIQUE, token());
        return switch (field.trim().toLowerCase()) {
            case "name" -> new Customer(null, value, draft.email(), draft.status());
            case "email" -> new Customer(null, draft.name(), value, draft.status());
            case "status" -> new Customer(null, draft.name(), draft.email(), value);
            default -> throw new IllegalArgumentException(
                    "Unknown customer request field '" + field + "'. Use name, email or status.");
        };
    }

    /**
     * The scenario's token, created on first use.
     *
     * @return 8 hex characters, the same for the whole scenario
     */
    private String token() {
        if (!session.has(TOKEN)) session.put(TOKEN, TestData.token());
        return session.get(TOKEN);
    }
}
