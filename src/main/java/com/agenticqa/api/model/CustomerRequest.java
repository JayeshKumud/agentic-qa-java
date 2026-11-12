package com.agenticqa.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Request body of {@code POST /customers} and {@code PUT /customers/{id}} as a plain Java object (POJO).
 * <p>
 * Any POJO, record or map can be a request body: {@link com.agenticqa.api.EndpointClient} turns it into JSON
 * with Jackson, so a typed request needs no extra code:
 * <pre>
 * CustomerRequest body = new CustomerRequest()
 *         .setName("Ada Lovelace " + token)
 *         .setEmail("ada." + token + "@example.test")
 *         .setStatus("inactive");
 * APIResponse created = new EndpointClient(session).call("customers.create", body);
 * </pre>
 * Fields left null are not sent ({@link JsonInclude.Include#NON_NULL}), so the server applies its defaults.
 * To send something a POJO cannot hold (a wrong type, an extra field), turn it into a
 * {@link com.agenticqa.api.Payload}: {@code Payload.of(body).set("status", 42)}.
 * <p>
 * Setters return {@code this}, so a body can be built in one expression; Jackson still sees normal getters and
 * setters. Use a POJO when the body is stable and used from Java; use a JSON template plus {@code Payload} (or the
 * generic steps) when tests change it field by field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerRequest {

    private String name;
    private String email;
    private String status;

    /** An empty request; set the fields you need. */
    public CustomerRequest() {}

    /**
     * A request with every field.
     *
     * @param name   display name
     * @param email  email address
     * @param status for example "active" or "inactive"; null lets the server choose
     */
    public CustomerRequest(String name, String email, String status) {
        this.name = name;
        this.email = email;
        this.status = status;
    }

    /** @return the display name */
    public String getName() {
        return name;
    }

    /**
     * @param name the display name
     * @return this request
     */
    public CustomerRequest setName(String name) {
        this.name = name;
        return this;
    }

    /** @return the email address */
    public String getEmail() {
        return email;
    }

    /**
     * @param email the email address
     * @return this request
     */
    public CustomerRequest setEmail(String email) {
        this.email = email;
        return this;
    }

    /** @return the status, or null */
    public String getStatus() {
        return status;
    }

    /**
     * @param status for example "active" or "inactive"
     * @return this request
     */
    public CustomerRequest setStatus(String status) {
        this.status = status;
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CustomerRequest that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(email, that.email)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, status);
    }

    @Override
    public String toString() {
        return "CustomerRequest[name=" + name + ", email=" + email + ", status=" + status + "]";
    }
}
