package com.agenticqa.steps;

import com.agenticqa.api.Customer;
import com.agenticqa.api.CustomerApi;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;
import java.util.function.Consumer;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.REQUEST;
import static com.agenticqa.steps.Keys.RESPONSE;
import static com.agenticqa.steps.Keys.TOKEN;

/**
 * Steps that build a customer request in the feature file, change it in later steps, and then send it.
 * <p>
 * The request body lives in the scenario's TestSession under {@link Keys#REQUEST} as JSON text. It can start from
 * a table, from a JSON file under src/test/resources/testdata, or from a raw JSON doc string; any number of later
 * steps can set or remove fields before it is sent. Sending it stores the API's answer under {@link Keys#CUSTOMER}
 * and {@link Keys#RESPONSE}, the same keys the other API and UI steps use, so any later step (for example a
 * browser search) works on the customer the request created.
 * <p>
 * Values, files and raw bodies may contain {@code {unique}}: it is replaced by one random token per scenario, the
 * same token everywhere, so names and emails stay related and parallel scenarios never collide.
 * <p>
 * Field steps accept any field name, so a request can also carry fields the API does not know. A raw body is sent
 * exactly as written; field steps only work while the body is a JSON object.
 */
public class CustomerRequestSteps {

    private static final String UNIQUE = "{unique}";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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
     * Fields not given are not sent (status then gets the server default, "active"). Values are sent as strings.
     *
     * @param table field/value rows
     */
    @Given("a new customer request:")
    public void newRequest(DataTable table) {
        ObjectNode body = MAPPER.createObjectNode();
        table.asMap(String.class, String.class).forEach((field, value) -> body.put(field.trim(), unique(value)));
        session.put(REQUEST, write(body));
    }

    /**
     * Starts a new request from a JSON file, for example
     * {@code Given a new customer request from "customers/new_customer.json"}.
     *
     * @param path path inside src/test/resources/testdata
     */
    @Given("a new customer request from {string}")
    public void newRequestFromFile(String path) {
        session.put(REQUEST, unique(TestData.file(path)));
    }

    /**
     * Starts a new request from a raw JSON doc string. The text is sent exactly as written (after {unique} is
     * replaced), so it may hold wrong types, extra fields or even be malformed:
     * <pre>
     *   Given the customer request body is:
     *     """
     *     { "name": 42, "email": "x.{unique}@example.test" }
     *     """
     * </pre>
     *
     * @param body the request body
     */
    @Given("the customer request body is:")
    public void rawBody(String body) {
        session.put(REQUEST, unique(body));
    }

    /**
     * Changes one field of the request, or adds it.
     *
     * @param field the field name
     * @param value the new value (sent as a string), may contain {unique}
     */
    @Given("the request field {string} is set to {string}")
    public void setField(String field, String value) {
        edit(body -> body.put(field.trim(), unique(value)));
    }

    /**
     * Changes several fields of the request; the others keep their values.
     *
     * @param table field/value rows
     */
    @Given("the request fields are updated:")
    public void updateFields(DataTable table) {
        Map<String, String> rows = table.asMap(String.class, String.class);
        edit(body -> rows.forEach((field, value) -> body.put(field.trim(), unique(value))));
    }

    /**
     * Removes a field, so it is not sent at all (for example to test a missing required field).
     *
     * @param field the field name
     * @throws IllegalStateException if the request has no such field, which is almost always a typo
     */
    @Given("the request field {string} is removed")
    public void removeField(String field) {
        edit(body -> {
            if (body.remove(field.trim()) == null) {
                throw new IllegalStateException("The request has no field '" + field + "' to remove. Body: "
                        + body);
            }
        });
    }

    /** POST /customers with the request; on success the created customer becomes "the customer". */
    @When("I send the create customer request")
    public void sendCreate() {
        APIResponse response = customers.createFromJson(session.get(REQUEST));
        session.put(RESPONSE, response);
        if (response.status() == 201) {
            Customer created = CustomerApi.asCustomer(response);
            cleanup.track(created.id());
            session.put(CUSTOMER, created);
            // later edits start from what the server stored, including defaults such as status
            session.put(REQUEST, write(MAPPER.valueToTree(created.withId(null))));
        }
    }

    /** PUT /customers/{id} of "the customer" with the request; on success the answer becomes "the customer". */
    @When("I send the update request for the customer")
    public void sendUpdate() {
        APIResponse response = customers.updateFromJson(session.get(CUSTOMER).id(), session.get(REQUEST));
        session.put(RESPONSE, response);
        if (response.status() == 200) {
            session.put(CUSTOMER, CustomerApi.asCustomer(response));
        }
    }

    /**
     * Parses the request, applies a change and stores it again.
     *
     * @param change what to do with the JSON object
     * @throws IllegalStateException if the current body is not a JSON object
     */
    private void edit(Consumer<ObjectNode> change) {
        String text = session.get(REQUEST);
        JsonNode node;
        try {
            node = MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The request body is not valid JSON, so its fields cannot be changed. "
                    + "Body: " + text, e);
        }
        if (!(node instanceof ObjectNode body)) {
            throw new IllegalStateException("The request body is not a JSON object, so its fields cannot be "
                    + "changed. Body: " + text);
        }
        change.accept(body);
        session.put(REQUEST, write(body));
    }

    private static String write(JsonNode body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Replaces {unique} by the scenario's token.
     *
     * @param raw text from the feature file or a test data file
     * @return the text with the token filled in
     */
    private String unique(String raw) {
        return raw.contains(UNIQUE) ? raw.replace(UNIQUE, token()) : raw;
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
