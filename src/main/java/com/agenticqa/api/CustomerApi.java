package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.APIResponse;

import java.util.Map;

/**
 * Shortcuts for the customers endpoints that many steps use. It holds no URLs, request classes or per-field
 * methods: the endpoints are declared under {@code endpoints:} in {@code src/main/resources/config/common.yaml} and bodies are
 * {@link Payload}s, so a new endpoint needs no change here.
 * <p>
 * Such a facade is optional. Code that calls an endpoint once can use {@link EndpointClient} directly, and
 * feature files use the generic steps ({@code When I call "customers.get" as ...}).
 */
public class CustomerApi {

    /** Name of the service under {@code services:} in the environment file. */
    public static final String SERVICE = "customers";

    private final EndpointClient api;

    /**
     * Creates the shortcuts for one scenario.
     *
     * @param session the per-scenario session
     */
    public CustomerApi(TestSession session) {
        this.api = new EndpointClient(session);
    }

    /**
     * GET /customers: lists all customers.
     *
     * @return the response (200 with a JSON array)
     */
    public APIResponse list() {
        return api.call("customers.list");
    }

    /**
     * GET /customers/{id}: reads one customer.
     *
     * @param id the customer id
     * @return the response (200 with the customer, 404 if it does not exist)
     */
    public APIResponse getById(long id) {
        return api.call("customers.get", Map.of("id", id));
    }

    /**
     * POST /customers: creates a customer.
     *
     * @param body a {@link Payload}, a map, a record, or JSON text sent exactly as written
     * @return the response (201 with the created customer, 422 if invalid)
     */
    public APIResponse create(Object body) {
        return api.call("customers.create", body);
    }

    /**
     * PUT /customers/{id}: replaces a customer's name, email and status.
     *
     * @param id   the customer id
     * @param body a {@link Payload}, a map, a record, or JSON text sent exactly as written
     * @return the response (200 with the updated customer, 422 if invalid)
     */
    public APIResponse update(long id, Object body) {
        return api.call("customers.update", Map.of("id", id), body);
    }

    /**
     * DELETE /customers/{id}: deletes one customer.
     *
     * @param id the customer id
     * @return the response (204 when deleted)
     */
    public APIResponse deleteById(long id) {
        return api.call("customers.delete", Map.of("id", id));
    }

    /**
     * Reads a response body as a customer.
     *
     * @param response a response whose body is one customer
     * @return the customer
     */
    public static Customer asCustomer(APIResponse response) {
        return ApiClient.read(response, Customer.class);
    }
}
