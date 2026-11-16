package com.agenticqa.steps;

import com.agenticqa.api.ApiClient;
import com.agenticqa.api.ApiFactory;
import com.agenticqa.api.Customer;
import com.agenticqa.api.CustomerApi;
import com.agenticqa.apis.CustomerValidationApi;
import com.agenticqa.core.TestData;
import com.agenticqa.core.TestSession;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.util.Map;

import static com.agenticqa.steps.Keys.CUSTOMER;

/**
 * Steps that use an API class of the existing framework's style ({@link CustomerValidationApi}, from
 * {@link ApiFactory}). The created customer becomes "the customer" of the scenario, so the GUI and the other
 * customer steps work on it, and it is deleted after the scenario.
 */
public class CustomerValidationSteps {

    private final ServiceCleanup cleanup;

    /**
     * Receives this scenario's cleanup from PicoContainer.
     *
     * @param cleanup deletes what the scenario created, after it
     */
    public CustomerValidationSteps(ServiceCleanup cleanup) {
        this.cleanup = cleanup;
    }

    /**
     * Creates a customer through REST Assured: {@code When I post a customer named "Ada" with the validation API}.
     *
     * @param name the readable name; a unique token is appended and the email is derived from it
     */
    @When("I post a customer named {string} with the validation API")
    public void postCustomer(String name) {
        String unique = name + " " + TestData.token();
        Response response = ApiFactory.getApi(CustomerValidationApi.class)
                .postCustomer(Map.of("name", unique, "email", TestData.emailFor(unique)));
        Customer customer = ApiClient.read(response.asString(), Customer.class);
        TestSession.current().put(CUSTOMER, customer);
        cleanup.delete(CustomerApi.SERVICE, CustomerApi.pathOf(customer.id()));
    }
}
