package com.agenticqa.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A customer of the sample app. The same record is used for requests and responses: when creating or updating,
 * leave {@code id} null and it is not sent; responses always carry the id assigned by the server.
 *
 * @param id     server-assigned id, null in requests
 * @param name   display name
 * @param email  email address
 * @param status for example "active"; null lets the server apply its default ("active")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Customer(Long id, String name, String email, String status) {

    /**
     * Builds a request body for a new customer with the server's default status.
     *
     * @param name  display name
     * @param email email address
     * @return a customer without id and status
     */
    public static Customer newCustomer(String name, String email) {
        return new Customer(null, name, email, null);
    }

    /**
     * Copy of this customer with another id; null removes the id from the JSON.
     *
     * @param newId the id to set, or null
     * @return the changed copy
     */
    public Customer withId(Long newId) {
        return new Customer(newId, name, email, status);
    }

    /**
     * Copy of this customer with another status, for update requests.
     *
     * @param newStatus the status to set
     * @return the changed copy
     */
    public Customer withStatus(String newStatus) {
        return new Customer(id, name, email, newStatus);
    }
}