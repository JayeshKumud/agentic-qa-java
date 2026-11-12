package com.agenticqa.api;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class of the HTTP clients (ServiceClient, which EndpointClient uses). It sends requests through the scenario's
 * Playwright API context, so API calls share cookies with the browser. Subclasses only describe business
 * operations; URL building, timeout, default headers, JSON, logging and the call log live here, once.
 */
public abstract class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TestSession session;
    private final String baseUrl;
    private final int timeoutMs;
    private final Map<String, String> defaultHeaders;

    /**
     * Creates a client for one configured service.
     *
     * @param session the per-scenario session that provides the API context
     * @param service the service settings (base URL, timeout, default headers)
     */
    protected ApiClient(TestSession session, Settings.Service service) {
        this.session = session;
        this.baseUrl = service.baseUrl();
        this.timeoutMs = service.timeoutMs();
        this.defaultHeaders = service.headers();
    }

    /**
     * Sends a GET request.
     *
     * @param path path of the endpoint, for example /customers
     * @return the raw response
     */
    protected APIResponse get(String path) {
        return send("GET", path, null, Map.of());
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param path path of the endpoint
     * @param body any object Jackson can serialize (record, map, bean)
     * @return the raw response
     */
    protected APIResponse post(String path, Object body) {
        return send("POST", path, toJson(body), Map.of());
    }

    /**
     * Sends a POST request with a JSON body that is already text. The text is sent exactly as given, so it may
     * contain extra fields, wrong types or even be malformed (for negative tests).
     *
     * @param path path of the endpoint
     * @param json the request body
     * @return the raw response
     */
    protected APIResponse postJson(String path, String json) {
        return send("POST", path, json, Map.of());
    }

    /**
     * Sends a PUT request with a JSON body.
     *
     * @param path path of the endpoint
     * @param body any object Jackson can serialize
     * @return the raw response
     */
    protected APIResponse put(String path, Object body) {
        return send("PUT", path, toJson(body), Map.of());
    }

    /**
     * Sends a PUT request with a JSON body that is already text, exactly as given.
     *
     * @param path path of the endpoint
     * @param json the request body
     * @return the raw response
     */
    protected APIResponse putJson(String path, String json) {
        return send("PUT", path, json, Map.of());
    }

    /**
     * Sends a DELETE request.
     *
     * @param path path of the endpoint
     * @return the raw response
     */
    protected APIResponse delete(String path) {
        return send("DELETE", path, null, Map.of());
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
        return read(response.text(), type);
    }

    /**
     * Parses a JSON text into a typed object (record or bean).
     *
     * @param json the JSON text
     * @param type the target type
     * @param <T>  the target type
     * @return the parsed object
     * @throws UncheckedIOException if the text does not match the type
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot read " + type.getSimpleName() + " from: " + json, e);
        }
    }

    /**
     * Sends one request with the service's timeout and headers, logs one line and records the call in the
     * session's API call log (attached to the report when the scenario fails).
     *
     * @param method  HTTP verb: GET, POST, PUT, PATCH, DELETE, HEAD or OPTIONS
     * @param path    path of the endpoint, appended to the base URL
     * @param json    the JSON body as text, or null for none
     * @param headers extra headers for this request; they win over the service's default headers
     * @return the raw response
     */
    protected APIResponse send(String method, String path, String json, Map<String, String> headers) {
        String verb = method.trim().toUpperCase();
        String url = baseUrl + path;
        Map<String, String> allHeaders = new LinkedHashMap<>(defaultHeaders);
        if (json != null) allHeaders.putIfAbsent("Content-Type", "application/json");
        allHeaders.putAll(headers);

        RequestOptions options = RequestOptions.create().setMethod(verb).setTimeout(timeoutMs);
        allHeaders.forEach(options::setHeader);
        if (json != null) options.setData(json);

        APIRequestContext api = session.api();
        long started = System.nanoTime();
        APIResponse response = api.fetch(url, options);
        long ms = (System.nanoTime() - started) / 1_000_000;

        System.out.printf("[API] [%s] %s %s -> %d (%d ms)%n", Thread.currentThread().getName(), verb, url,
                response.status(), ms);
        session.recordApiCall(new ApiCall(verb, url, allHeaders, json, response.status(), response.text(), ms));
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
