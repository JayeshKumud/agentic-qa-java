package com.agenticqa.api;

import com.agenticqa.core.JsonPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The named requests and responses of one scenario. A scenario can build several requests side by side (for
 * example "customer", "order" and "payment"), change each of them over many steps, send them to different
 * services, and read any earlier response by name. PicoContainer creates one instance per scenario and shares it
 * with every step class.
 * <p>
 * A request body is kept as JSON text, not as an object, so a raw body may also hold wrong types, extra fields
 * or be malformed (for negative tests). Field edits parse it, change it and write it back.
 */
public class ApiState {

    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<String, Reply> replies = new LinkedHashMap<>();

    /** A request being built: its body text, headers of its own, and path parameters for catalog endpoints. */
    public static final class Request {
        private String body;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> pathParams = new LinkedHashMap<>();

        private Request(String body) {
            this.body = body;
        }

        /** @return the body as JSON text, or null for a request without body */
        public String body() {
            return body;
        }

        /** @return headers sent with this request only (changeable) */
        public Map<String, String> headers() {
            return headers;
        }

        /** @return values for the {name} parts of a catalog endpoint's path (changeable) */
        public Map<String, String> pathParams() {
            return pathParams;
        }
    }

    /**
     * A stored response. Status and body are copied out of Playwright's response, so they stay readable after
     * the browser context is gone.
     *
     * @param status  HTTP status
     * @param body    body text
     * @param headers response headers
     */
    public record Reply(int status, String body, Map<String, String> headers) {

        /**
         * Copies a Playwright response.
         *
         * @param response the response
         * @return the stored copy
         */
        public static Reply of(APIResponse response) {
            return new Reply(response.status(), response.text(), Map.copyOf(response.headers()));
        }

        /**
         * The body as JSON.
         *
         * @return the parsed body
         * @throws IllegalArgumentException if the body is not JSON
         */
        public JsonNode json() {
            return JsonPaths.parse(body);
        }
    }

    /**
     * Starts (or restarts) a named request.
     *
     * @param name the request name
     * @param body the body as JSON text, may be null
     * @return the new request
     */
    public synchronized Request start(String name, String body) {
        Request request = new Request(body);
        requests.put(name, request);
        return request;
    }

    /**
     * A request started earlier.
     *
     * @param name the request name
     * @return the request
     * @throws IllegalStateException if no request has that name
     */
    public synchronized Request request(String name) {
        Request request = requests.get(name);
        if (request == null) {
            throw new IllegalStateException("No request named '" + name + "'. Requests so far: " + requests.keySet()
                    + ". Start one with: Given a new \"" + name + "\" request ...");
        }
        return request;
    }

    /**
     * Tells whether a request exists.
     *
     * @param name the request name
     * @return true if it was started
     */
    public synchronized boolean hasRequest(String name) {
        return requests.containsKey(name);
    }

    /**
     * Changes a request's body as JSON.
     *
     * @param name   the request name
     * @param change what to do with the parsed body (an object or array)
     * @throws IllegalStateException if the body is missing or not a JSON object or array
     */
    public synchronized void edit(String name, Consumer<JsonNode> change) {
        Request request = request(name);
        if (request.body == null) request.body = "{}";
        JsonNode body;
        try {
            body = JsonPaths.parse(request.body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The body of request '" + name + "' is not valid JSON, so its fields "
                    + "cannot be changed. Body: " + request.body, e);
        }
        if (!body.isContainerNode()) {
            throw new IllegalStateException("The body of request '" + name + "' is not a JSON object or array: "
                    + request.body);
        }
        change.accept(body);
        request.body = JsonPaths.write(body);
    }

    /**
     * Stores a response under a name, replacing an older one with the same name.
     *
     * @param name  the response name
     * @param reply the response
     */
    public synchronized void store(String name, Reply reply) {
        replies.put(name, reply);
    }

    /**
     * A stored response.
     *
     * @param name the response name (usually the name of the request that produced it)
     * @return the response
     * @throws IllegalStateException if no response has that name
     */
    public synchronized Reply reply(String name) {
        Reply reply = replies.get(name);
        if (reply == null) {
            throw new IllegalStateException("No response named '" + name + "'. Responses so far: " + replies.keySet());
        }
        return reply;
    }
}
