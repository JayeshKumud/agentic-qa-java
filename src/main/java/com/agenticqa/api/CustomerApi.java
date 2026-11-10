package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.APIResponse;

/**
 * Business operations of the customers endpoint of the sample app. Steps call these methods and never build
 * URLs or requests themselves.
 */
public class CustomerApi extends ApiClient {

    /** Name of the service under {@code services:} in the environment file. */
    public static final String SERVICE = "customers";

    private static final String CUSTOMERS = "/customers";

    /**
     * Creates the client for the "customers" service of the active environment.
     *
     * @param session the per-scenario session
     */
    public CustomerApi(TestSession session) {
        super(session, session.settings().service(SERVICE));
    }

    /**
     * GET /customers: lists all customers.
     *
     * @return the response (200 with a JSON array)
     */
    public APIResponse list() {
        return get(CUSTOMERS);
    }

    /**
     * GET /customers/{id}: reads one customer.
     *
     * @param id the customer id
     * @return the response (200 with the customer, 404 if it does not exist)
     */
    public APIResponse getById(long id) {
        return get(CUSTOMERS + "/" + id);
    }

    /**
     * POST /customers: creates a customer.
     *
     * @param customer the customer to create (id is ignored)
     * @return the response (201 with the created customer, 422 if invalid)
     */
    public APIResponse create(Customer customer) {
        return post(CUSTOMERS, customer.withId(null));
    }

    /**
     * POST /customers with a body written as JSON text, sent exactly as given (for requests built in feature
     * files and for negative tests).
     *
     * @param json the request body
     * @return the response (201 with the created customer, 422 if invalid)
     */
    public APIResponse createFromJson(String json) {
        return postJson(CUSTOMERS, json);
    }

    /**
     * PUT /customers/{id} with a body written as JSON text, sent exactly as given.
     *
     * @param id   the customer id
     * @param json the request body
     * @return the response (200 with the updated customer, 422 if invalid)
     */
    public APIResponse updateFromJson(long id, String json) {
        return putJson(CUSTOMERS + "/" + id, json);
    }

    /**
     * PUT /customers/{id}: replaces a customer's name, email and status.
     *
     * @param id       the customer id
     * @param customer the new values (its id is ignored)
     * @return the response (200 with the updated customer)
     */
    public APIResponse update(long id, Customer customer) {
        return put(CUSTOMERS + "/" + id, customer.withId(null));
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

    /**
     * DELETE /customers/{id}: deletes one customer.
     *
     * @param id the customer id
     * @return the response (204 when deleted)
     */
    public APIResponse deleteById(long id) {
        return delete(CUSTOMERS + "/" + id);
    }
}
