package com.agenticqa.api;

import com.agenticqa.api.Endpoints.Endpoint;
import com.agenticqa.core.TestSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Calls any endpoint of the catalog ({@link Endpoints}) by name. This one class replaces a client class with
 * one method per endpoint:
 * <pre>
 * EndpointClient api = new EndpointClient(session);
 * APIResponse created = api.call("customers.create", Payload.file("customers/new_customer.json").set("name", n));
 * APIResponse read    = api.call("customers.get", Map.of("id", id));
 * APIResponse updated = api.call("customers.update", Map.of("id", id), Payload.of(read).set("status", "inactive"));
 * </pre>
 * The service (base URL, timeout, default headers) comes from the endpoint file and the environment file, and
 * every call goes through {@link ApiClient#send}, so it is logged, recorded for the report and shares cookies
 * with the browser.
 */
public class EndpointClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TestSession session;

    /**
     * Creates the client for one scenario.
     *
     * @param session the per-scenario session
     */
    public EndpointClient(TestSession session) {
        this.session = session;
    }

    /**
     * Calls an endpoint without path parameters and without body.
     *
     * @param endpoint GROUP.KEY, for example customers.list
     * @return the response
     */
    public APIResponse call(String endpoint) {
        return call(endpoint, Map.of(), null, Map.of(), null);
    }

    /**
     * Calls an endpoint without path parameters.
     *
     * @param endpoint GROUP.KEY
     * @param body     the body: a {@link Payload}, JSON text (sent exactly as written), any object Jackson can
     *                 serialize, or null
     * @return the response
     */
    public APIResponse call(String endpoint, Object body) {
        return call(endpoint, Map.of(), body, Map.of(), null);
    }

    /**
     * Calls an endpoint without body.
     *
     * @param endpoint   GROUP.KEY
     * @param pathParams values for the {name} parts of the path
     * @return the response
     */
    public APIResponse call(String endpoint, Map<String, ?> pathParams) {
        return call(endpoint, pathParams, null, Map.of(), null);
    }

    /**
     * Calls an endpoint.
     *
     * @param endpoint   GROUP.KEY
     * @param pathParams values for the {name} parts of the path
     * @param body       the body (see {@link #call(String, Object)}), or null
     * @return the response
     */
    public APIResponse call(String endpoint, Map<String, ?> pathParams, Object body) {
        return call(endpoint, pathParams, body, Map.of(), null);
    }

    /**
     * Calls an endpoint, with every option.
     *
     * @param endpoint   GROUP.KEY
     * @param pathParams values for the {name} parts of the path
     * @param body       the body (see {@link #call(String, Object)}), or null
     * @param headers    headers for this call only; they win over the service's default headers
     * @param fallback   value of a path parameter missing from pathParams (for example a scenario variable of
     *                   the same name), or null
     * @return the response
     * @throws IllegalArgumentException if the endpoint or its service is unknown, or a path parameter is missing
     */
    public APIResponse call(String endpoint, Map<String, ?> pathParams, Object body, Map<String, String> headers,
                            UnaryOperator<String> fallback) {
        Endpoint e = Endpoints.get(endpoint);
        String path = e.path(pathParams, fallback);
        return new ServiceClient(session, e.service()).request(e.method(), path, toJson(body), headers);
    }

    private static String toJson(Object body) {
        if (body == null) return null;
        if (body instanceof String text) return text;
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
