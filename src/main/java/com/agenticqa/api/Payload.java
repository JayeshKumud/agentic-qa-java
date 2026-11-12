package com.agenticqa.api;

import com.agenticqa.core.JsonPaths;
import com.agenticqa.core.TestData;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;

import java.util.Map;

/**
 * A JSON request body for any endpoint, built and changed by path, so Java code needs no request class (record,
 * POJO, builder or {@code withX} method) per endpoint:
 * <pre>
 * Payload body = Payload.file("customers/new_customer.json")    // a template under src/test/resources/testdata
 *         .set("name", "Ada " + token)
 *         .set("profile.addresses[0].city", "Graz")              // nested paths are created if missing
 *         .set("limits.creditEur", 7500)                         // numbers, booleans, lists, maps, records
 *         .remove("status");
 * Payload update = Payload.of(response).remove("id").set("status", "inactive");   // from a response
 * Payload small  = Payload.of(Map.of("name", "Ada", "email", "ada@example.test"));
 * </pre>
 * Paths are those of {@link JsonPaths}. A payload is not thread-safe; each scenario builds its own.
 * Jackson serializes it as its JSON, so it can be passed wherever a body object is expected.
 */
public final class Payload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonNode root;

    private Payload(JsonNode root) {
        if (root == null || !root.isContainerNode()) {
            throw new IllegalArgumentException("A payload must be a JSON object or array, got: " + root);
        }
        this.root = root;
    }

    /**
     * An empty JSON object.
     *
     * @return {}
     */
    public static Payload empty() {
        return new Payload(MAPPER.createObjectNode());
    }

    /**
     * A body from a test data file. Placeholders such as {unique} are not replaced (Java code sets its values
     * directly; feature files use the steps, which do replace them).
     *
     * @param path path inside src/test/resources/testdata
     * @return the payload
     */
    public static Payload file(String path) {
        return json(TestData.file(path));
    }

    /**
     * A body from JSON text.
     *
     * @param json the JSON
     * @return the payload
     * @throws IllegalArgumentException if the text is not a JSON object or array
     */
    public static Payload json(String json) {
        return new Payload(JsonPaths.parse(json));
    }

    /**
     * A body from the JSON of a response, for example to send back what a GET returned.
     *
     * @param response the response
     * @return the payload
     */
    public static Payload of(APIResponse response) {
        return json(response.text());
    }

    /**
     * A body from any object Jackson can serialize: a map, a record, a bean or a JsonNode.
     *
     * @param value the object
     * @return the payload (a copy; later changes do not touch the object)
     */
    public static Payload of(Object value) {
        if (value instanceof Payload payload) return payload.copy();
        if (value instanceof JsonNode node) return new Payload(node.deepCopy());
        return new Payload(MAPPER.valueToTree(value));
    }

    /**
     * Sets a value by path, creating missing objects and arrays on the way.
     *
     * @param path  for example name, profile.addresses[0].city or tags[] (append)
     * @param value text, number, boolean, null, list, map, record or JsonNode
     * @return this payload
     */
    public Payload set(String path, Object value) {
        JsonNode json = value == null ? MAPPER.nullNode()
                : value instanceof JsonNode node ? node : MAPPER.valueToTree(value);
        JsonPaths.set(root, path, json);
        return this;
    }

    /**
     * Sets several values; keys are paths.
     *
     * @param values path to value
     * @return this payload
     */
    public Payload setAll(Map<String, ?> values) {
        values.forEach(this::set);
        return this;
    }

    /**
     * Removes a field or an array element.
     *
     * @param path the path
     * @return this payload
     * @throws IllegalArgumentException if there is nothing at that path (almost always a typo)
     */
    public Payload remove(String path) {
        JsonPaths.remove(root, path);
        return this;
    }

    /**
     * Deep-merges JSON into this body (JSON Merge Patch: null removes a field).
     *
     * @param patch a JSON object
     * @return this payload
     */
    public Payload merge(String patch) {
        JsonPaths.merge(root, JsonPaths.parse(patch));
        return this;
    }

    /**
     * Reads a value by path.
     *
     * @param path the path
     * @return the value, or null if the path leads nowhere
     */
    public JsonNode get(String path) {
        return JsonPaths.get(root, path);
    }

    /**
     * Reads the body into a typed object, when a step wants one.
     *
     * @param type the target type
     * @param <T>  the target type
     * @return the object
     */
    public <T> T as(Class<T> type) {
        return MAPPER.convertValue(root, type);
    }

    /**
     * A deep copy, to change without touching this payload.
     *
     * @return the copy
     */
    public Payload copy() {
        return new Payload(root.deepCopy());
    }

    /** @return the JSON tree (what Jackson writes when it serializes this payload) */
    @JsonValue
    public JsonNode node() {
        return root;
    }

    /** @return the body as compact JSON text */
    @Override
    public String toString() {
        return JsonPaths.write(root);
    }
}
