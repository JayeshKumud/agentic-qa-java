package com.agenticqa.steps;

import com.agenticqa.api.ApiState;
import com.agenticqa.api.ApiState.Reply;
import com.agenticqa.api.EndpointClient;
import com.agenticqa.api.Endpoints;
import com.agenticqa.api.ServiceClient;
import com.agenticqa.core.JsonPaths;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import com.agenticqa.core.Variables;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.agenticqa.steps.Keys.RESPONSE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Generic HTTP steps for any configured service, so one scenario can drive several microservices (and the GUI,
 * through the other step classes) without new Java code per endpoint.
 * <ul>
 *   <li>Requests have names. Several can be built side by side and changed over any number of steps.</li>
 *   <li>Bodies start from a JSON file under src/test/resources/testdata, a table, a raw doc string or an earlier
 *       response, and are edited by path: {@code profile.addresses[0].city}, {@code tags[]} (append), ...</li>
 *   <li>Values are text; prefix {@code json:} for numbers, booleans, null, objects or arrays: {@code json:3}.</li>
 *   <li>{@code ${name}} uses a value saved from an earlier response; {@code {unique}} is the scenario's token.
 *       Both work in values, files, bodies, URLs, headers and expectations.</li>
 *   <li>A response is stored under the name of the request that produced it (or the name given for calls
 *       without body), and also as the last response for {@code the API responds with status ...}.</li>
 *   <li>Endpoints of the catalog (src/main/resources/endpoints) are called by name, {@code "customers.get"}:
 *       method, path and service come from the catalog, so feature files hold no URLs.</li>
 * </ul>
 */
public class HttpSteps {

    private final TestSession session;
    private final ApiState api;
    private final Variables vars;
    private final ServiceCleanup cleanup;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param api     the scenario's named requests and responses
     * @param vars    the scenario's variables
     * @param cleanup the scenario's cleanup list
     */
    public HttpSteps(TestSession session, ApiState api, Variables vars, ServiceCleanup cleanup) {
        this.session = session;
        this.api = api;
        this.vars = vars;
        this.cleanup = cleanup;
    }

    // --------------------------------------------------------------------------------------- building requests

    /**
     * Starts a request from a JSON file: {@code Given a new "order" request from "orders/large_order.json"}.
     *
     * @param name the request name
     * @param path path inside src/test/resources/testdata
     */
    @Given("a new {string} request from {string}")
    public void fromFile(String name, String path) {
        api.start(name, vars.resolve(TestData.file(path)));
    }

    /**
     * Starts a request from a table of path and value; missing objects and arrays are created.
     * <pre>
     *   | customer.name     | Ada {unique} |
     *   | items[0].sku      | SKU-1        |
     *   | items[0].quantity | json:2       |
     * </pre>
     *
     * @param name  the request name
     * @param table path/value rows
     */
    @Given("a new {string} request:")
    public void fromTable(String name, DataTable table) {
        api.start(name, "{}");
        setFields(name, table);
    }

    /**
     * Starts a request from a raw doc string, sent exactly as written after placeholders are filled in.
     *
     * @param name the request name
     * @param body the body
     */
    @Given("a new {string} request with body:")
    public void fromDocString(String name, String body) {
        api.start(name, vars.resolve(body));
    }

    /**
     * Starts a request from the body of an earlier response, for example to send back what a GET returned.
     *
     * @param name     the new request name
     * @param response the response name
     */
    @Given("a new {string} request from the {string} response")
    public void fromResponse(String name, String response) {
        api.start(name, api.reply(response).body());
    }

    /**
     * Starts a request from one part of an earlier response, for example {@code data.items[0]}.
     *
     * @param name     the new request name
     * @param response the response name
     * @param path     the part to copy
     */
    @Given("a new {string} request from the {string} response field {string}")
    public void fromResponseField(String name, String response, String path) {
        JsonNode part = JsonPaths.get(api.reply(response).json(), path);
        assertNotNull(part, "The '" + response + "' response has no field '" + path + "'");
        api.start(name, JsonPaths.write(part));
    }

    /**
     * Starts a request from the default body of a catalog endpoint, or from {} if it has none:
     * {@code Given a new "ada" request for "customers.create"}.
     *
     * @param name     the request name
     * @param endpoint FILE.KEY, for example customers.create
     */
    @Given("a new {string} request for {string}")
    public void forEndpoint(String name, String endpoint) {
        String template = Endpoints.get(endpoint).body();
        api.start(name, template == null ? "{}" : vars.resolve(TestData.file(template)));
    }

    /**
     * Sets a path parameter of a request for a catalog endpoint, for example the {id} of /customers/{id}.
     *
     * @param name  the request name
     * @param param the parameter name
     * @param value the value, may contain ${variables}
     */
    @Given("the {string} request path parameter {string} is {string}")
    public void pathParam(String name, String param, String value) {
        api.request(name).pathParams().put(param, vars.resolve(value));
    }

    /**
     * Sets one value of a request; paths may be nested and create what is missing.
     *
     * @param name  the request name
     * @param path  for example customer.address.city or items[1].quantity
     * @param value the value; text unless prefixed with json:
     */
    @Given("the {string} request field {string} is set to {string}")
    public void setField(String name, String path, String value) {
        JsonNode resolved = JsonPaths.value(vars.resolve(value));
        api.edit(name, body -> JsonPaths.set(body, path, resolved));
    }

    /**
     * Sets several values of a request at once; the rest of the body stays as it is.
     *
     * @param name  the request name
     * @param table path/value rows
     */
    @Given("the {string} request fields are set:")
    public void setFields(String name, DataTable table) {
        Map<String, String> rows = table.asMap(String.class, String.class);
        api.edit(name, body -> rows.forEach((path, value) ->
                JsonPaths.set(body, path.trim(), JsonPaths.value(vars.resolve(value)))));
    }

    /**
     * Removes a field or array element, so it is not sent at all.
     *
     * @param name the request name
     * @param path the path
     */
    @Given("the {string} request field {string} is removed")
    public void removeField(String name, String path) {
        api.edit(name, body -> JsonPaths.remove(body, path));
    }

    /**
     * Deep-merges a JSON block into a request: objects merge field by field, null removes a field, anything else
     * replaces. Handy for changing many nested values of a large payload in one step.
     *
     * @param name  the request name
     * @param patch the JSON object to merge
     */
    @Given("the {string} request is merged with:")
    public void merge(String name, String patch) {
        JsonNode changes = JsonPaths.parse(vars.resolve(patch));
        api.edit(name, body -> JsonPaths.merge(body, changes));
    }

    /**
     * Adds a header to one request, for example a correlation id or a token saved from a login response.
     *
     * @param name   the request name
     * @param header the header name
     * @param value  the value
     */
    @Given("the {string} request header {string} is {string}")
    public void header(String name, String header, String value) {
        api.request(name).headers().put(header, vars.resolve(value));
    }

    /**
     * Checks a value of a request before it is sent.
     *
     * @param name     the request name
     * @param path     the path
     * @param expected the expected value; text unless prefixed with json:
     */
    @Then("the {string} request field {string} is {string}")
    public void requestFieldIs(String name, String path, String expected) {
        JsonNode body = JsonPaths.parse(api.request(name).body());
        JsonNode actual = JsonPaths.get(body, path);
        String want = vars.resolve(expected);
        assertTrue(JsonPaths.matches(actual, want),
                "Request '" + name + "' field '" + path + "' is " + actual + ", expected " + want);
    }

    // ---------------------------------------------------------------------------------------------- sending

    /**
     * Sends a named request: {@code When I send the "order" request as POST to "/orders" on the "orders" service}.
     * The response is stored under the request's name.
     *
     * @param name    the request name
     * @param method  POST, PUT, PATCH, DELETE, ...
     * @param path    the endpoint path, may contain ${variables}
     * @param service the service name from the environment file
     */
    @When("I send the {string} request as {word} to {string} on the {string} service")
    public void send(String name, String method, String path, String service) {
        ApiState.Request request = api.request(name);
        APIResponse response = new ServiceClient(session, service)
                .request(method, vars.resolve(path), request.body(), request.headers());
        remember(name, response);
    }

    /**
     * Sends a request without body: {@code When I send GET "/customers/${adaId}" to the "customers" service as "ada"}.
     *
     * @param method  GET, DELETE, HEAD, ...
     * @param path    the endpoint path, may contain ${variables}
     * @param service the service name
     * @param name    the name to store the response under
     */
    @When("I send {word} {string} to the {string} service as {string}")
    public void sendWithoutBody(String method, String path, String service, String name) {
        APIResponse response = new ServiceClient(session, service).request(method, vars.resolve(path), null, Map.of());
        remember(name, response);
    }

    /**
     * Calls a catalog endpoint with a named request: {@code When I call "customers.update" with the "ada" request}.
     * Path parameters come from the request, else from variables of the same name. The response is stored
     * under the request's name.
     *
     * @param endpoint FILE.KEY
     * @param name     the request name
     */
    @When("I call {string} with the {string} request")
    public void callWithRequest(String endpoint, String name) {
        ApiState.Request request = api.request(name);
        APIResponse response = new EndpointClient(session)
                .call(endpoint, request.pathParams(), request.body(), request.headers(), vars::find);
        remember(name, response);
    }

    /**
     * Calls a catalog endpoint without body: {@code When I call "customers.list" as "all"}. Path parameters come
     * from variables of the same name.
     *
     * @param endpoint FILE.KEY
     * @param name     the name to store the response under
     */
    @When("I call {string} as {string}")
    public void call(String endpoint, String name) {
        remember(name, new EndpointClient(session).call(endpoint, Map.of(), null, Map.of(), vars::find));
    }

    /**
     * Calls a catalog endpoint without body, with path parameters from a table:
     * <pre>
     * When I call "customers.get" as "adaRead" with:
     *   | id | ${adaId} |
     * </pre>
     *
     * @param endpoint FILE.KEY
     * @param name     the name to store the response under
     * @param table    parameter/value rows
     */
    @When("I call {string} as {string} with:")
    public void callWithParams(String endpoint, String name, DataTable table) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        table.asMap(String.class, String.class).forEach((param, value) -> params.put(param.trim(), vars.resolve(value)));
        remember(name, new EndpointClient(session).call(endpoint, params, null, Map.of(), vars::find));
    }

    /**
     * Deletes a resource after the scenario, pass or fail, for example
     * {@code And "/orders/${orderId}" on the "orders" service is deleted after the scenario}.
     *
     * @param path    the resource path, may contain ${variables} (filled in now)
     * @param service the service name
     */
    @Given("{string} on the {string} service is deleted after the scenario")
    public void deleteAfterScenario(String path, String service) {
        cleanup.delete(service, vars.resolve(path));
    }

    // ------------------------------------------------------------------------------------------- responses

    /**
     * Checks the status of a named response.
     *
     * @param name   the response name
     * @param status the expected status
     */
    @Then("the {string} response status is {int}")
    public void status(String name, int status) {
        Reply reply = api.reply(name);
        assertEquals(reply.status(), status, "Response '" + name + "': " + reply.body());
    }

    /**
     * Checks one value of a named response.
     *
     * @param name     the response name
     * @param path     the path, for example items[0].sku or $ for the whole body
     * @param expected the expected value; text unless prefixed with json:
     */
    @Then("the {string} response field {string} is {string}")
    public void responseFieldIs(String name, String path, String expected) {
        Reply reply = api.reply(name);
        JsonNode actual = JsonPaths.get(reply.json(), path);
        String want = vars.resolve(expected);
        assertTrue(JsonPaths.matches(actual, want), "Response '" + name + "' field '" + path + "' is " + actual
                + ", expected " + want + ". Body: " + reply.body());
    }

    /**
     * Checks several values of a named response and reports every mismatch at once.
     *
     * @param name  the response name
     * @param table path/expected rows
     */
    @Then("the {string} response fields are:")
    public void responseFieldsAre(String name, DataTable table) {
        Reply reply = api.reply(name);
        JsonNode body = reply.json();
        List<String> wrong = new ArrayList<>();
        table.asMap(String.class, String.class).forEach((path, expected) -> {
            JsonNode actual = JsonPaths.get(body, path.trim());
            String want = vars.resolve(expected);
            if (!JsonPaths.matches(actual, want)) wrong.add(path + " is " + actual + ", expected " + want);
        });
        if (!wrong.isEmpty()) fail("Response '" + name + "' differs:\n  " + String.join("\n  ", wrong)
                + "\nBody: " + reply.body());
    }

    /**
     * Checks the size of an array (or object) in a named response.
     *
     * @param name  the response name
     * @param path  the path of the array, $ for a body that is an array
     * @param count the expected number of items
     */
    @Then("the {string} response field {string} has {int} item(s)")
    public void responseFieldSize(String name, String path, int count) {
        JsonNode actual = JsonPaths.get(api.reply(name).json(), path);
        assertNotNull(actual, "Response '" + name + "' has no field '" + path + "'");
        assertTrue(actual.isContainerNode(), "Response '" + name + "' field '" + path + "' is not a list: " + actual);
        assertEquals(actual.size(), count, "Items in response '" + name + "' field '" + path + "'");
    }

    /**
     * Saves a value of a named response as a variable, for later use as ${variable}.
     *
     * @param name     the response name
     * @param path     the path, for example id or data.token
     * @param variable the variable name
     */
    @Given("I save the {string} response field {string} as {string}")
    public void save(String name, String path, String variable) {
        JsonNode value = JsonPaths.get(api.reply(name).json(), path);
        assertNotNull(value, "Response '" + name + "' has no field '" + path + "' to save. Body: "
                + api.reply(name).body());
        vars.put(variable, value.isValueNode() ? value.asText() : JsonPaths.write(value));
    }

    /**
     * Saves a fixed value as a variable, for example a value several requests share.
     *
     * @param variable the variable name
     * @param value    the value, may contain {unique} and ${other}
     */
    @Given("the variable {string} is {string}")
    public void variable(String variable, String value) {
        vars.put(variable, vars.resolve(value));
    }

    private void remember(String name, APIResponse response) {
        api.store(name, Reply.of(response));
        session.put(RESPONSE, response);
    }
}
