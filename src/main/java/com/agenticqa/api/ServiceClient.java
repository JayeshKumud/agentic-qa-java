package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.APIResponse;

import java.util.Map;

/**
 * A generic client for any configured service (microservice). Feature files address services by the name used
 * under {@code services:} in the environment file, so a scenario can call several services in a row.
 * Business clients such as {@link CustomerApi} stay the better choice for operations used in many steps.
 */
public class ServiceClient extends ApiClient {

    private final String name;

    /**
     * Creates a client for one service of the active environment.
     *
     * @param session the per-scenario session
     * @param name    the service name, for example "customers"
     * @throws IllegalArgumentException if the service is not configured
     */
    public ServiceClient(TestSession session, String name) {
        super(session, name, session.settings().service(name));
        this.name = name;
    }

    /**
     * Sends any request.
     *
     * @param method  HTTP verb, for example POST or PATCH
     * @param path    path of the endpoint, for example /orders/42
     * @param json    request body as JSON text, or null for none
     * @param headers headers for this request only
     * @return the raw response
     */
    public APIResponse request(String method, String path, String json, Map<String, String> headers) {
        return send(method, path, json, headers);
    }

    /** @return the service name */
    public String name() {
        return name;
    }
}
