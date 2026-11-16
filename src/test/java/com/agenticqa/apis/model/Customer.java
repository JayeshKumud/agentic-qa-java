package com.agenticqa.apis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A customer, as sent to and returned by the customers service. REST Assured serializes it with Jackson for a
 * request body ({@code CustomerApi.postCustomer(customer)}) and reads responses into it
 * ({@code response.as(Customer.class)}). A null field is left out of the request, so a new customer is sent
 * without an id; unknown response fields are ignored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Customer {

    private Long id;
    private String name;
    private String email;
    private String status;

    /** For Jackson. */
    public Customer() {}

    /**
     * A customer to create: id and status are set by the service.
     *
     * @param name  display name
     * @param email email address
     */
    public Customer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /**
     * A customer with every field, for example one read from the customers page.
     *
     * @param id     server-assigned id
     * @param name   display name
     * @param email  email address
     * @param status for example "active"
     */
    public Customer(Long id, String name, String email, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", name=" + name + ", email=" + email + ", status=" + status + "]";
    }
}
