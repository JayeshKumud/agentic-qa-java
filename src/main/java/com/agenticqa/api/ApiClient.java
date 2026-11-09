package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;

import java.io.UncheckedIOException;

/**
 * Base class of every service client (CustomerApi, OrderApi, ...). It sends requests through the scenario's
 * Playwright API context, so API calls share cookies with the browser. Subclasses only describe business
 * operations; URL building, timeout, JSON and logging live here, once.
 */
public abstract class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TestSession session;
    private final String baseUrl;

    /**
     * Creates a client for one service.
     *
     * @param session the per-scenario session that provides the API context and the timeout
     * @param baseUrl base URL of the service, for example http://127.0.0.1:8000
     */
    protected ApiClient(TestSession session, String baseUrl) {
        this.session = session;
        this.baseUrl = baseUrl;
    }

    /**
     * Sends a GET request.
     *
     * @param path path of the endpoint, for example /customers
     * @return the raw response
     */
    protected APIResponse get(String path) {
        return send("GET", path, null);
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param path path of the endpoint
     * @param body any object Jackson can serialize (record, map, bean)
     * @return the raw response
     */
    protected APIResponse post(String path, Object body) {
        return send("POST", path, body);
    }

    /**
     * Sends a PUT request with a JSON body.
     *
     * @param path path of the endpoint
     * @param body any object Jackson can serialize
     * @return the raw response
     */
    protected APIResponse put(String path, Object body) {
        return send("PUT", path, body);
    }

    /**
     * Sends a DELETE request.
     *
     * @param path path of the endpoint
     * @return the raw response
     */
    protected APIResponse delete(String path) {
        return send("DELETE", path, null);
    }

    /**
     * Parses the body of a response as JSON.
     *
     * @param response a response whose body is JSON
     * @return the parsed JSON tree
     * @throws UncheckedIOException if the body is not valid JSON
     */
    public static JsonNode json(APIResponse response) {
        try {
            return MAPPER.readTree(response.text());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Response is not JSON: " + response.text(), e);
        }
    }

    /**
     * Parses the body of a response into a typed object (record or bean).
     *
     * @param response a response whose body is JSON
     * @param type     the target type
     * @param <T>      the target type
     * @return the parsed object
     * @throws UncheckedIOException if the body does not match the type
     */
    public static <T> T read(APIResponse response, Class<T> type) {
        try {
            return MAPPER.readValue(response.text(), type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot read " + type.getSimpleName() + " from: " + response.text(), e);
        }
    }

    /**
     * Sends one request with the configured timeout and logs one line per call.
     *
     * @param method HTTP verb: GET, POST, PUT or DELETE
     * @param path   path of the endpoint, appended to the base URL
     * @param body   object to send as JSON, or null for none
     * @return the raw response
     */
    private APIResponse send(String method, String path, Object body) {
        String url = baseUrl + path;
        RequestOptions options = RequestOptions.create().setTimeout(session.settings().api().timeoutMs());
        if (body != null) {
            options.setHeader("Content-Type", "application/json").setData(toJson(body));
        }
        APIRequestContext api = session.api();
        APIResponse response = switch (method) {
            case "GET" -> api.get(url, options);
            case "POST" -> api.post(url, options);
            case "PUT" -> api.put(url, options);
            case "DELETE" -> api.delete(url, options);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
        System.out.printf("[API] %s %s -> %d%n", method, url, response.status());
        return response;
    }

    /**
     * Serializes a request body.
     *
     * @param body the object to serialize
     * @return its JSON text
     * @throws UncheckedIOException if the object cannot be serialized
     */
    private static String toJson(Object body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
