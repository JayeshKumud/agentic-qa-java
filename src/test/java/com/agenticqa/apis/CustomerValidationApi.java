package com.agenticqa.apis;

import com.agenticqa.api.Api;
import com.agenticqa.config.Config;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;

/**
 * Example API class in the style of the existing framework: created by {@code ApiFactory.getApi(...)} through
 * its no-argument constructor, base URL from {@link Config#getApiUrl}, calls made with REST Assured.
 * API classes ported from the existing framework follow the same pattern and live next to this one.
 */
public class CustomerValidationApi implements Api {
    private final String baseUrl;
    private final String customersPath;

    public CustomerValidationApi() {
        this.baseUrl = Config.getApiUrl("customers");
        this.customersPath = "/customers";
    }

    /**
     * POST /customers.
     *
     * @param customerToPost the body, serialized to JSON (a map, a record or a POJO)
     * @return the response
     */
    public Response postCustomer(Object customerToPost) {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .contentType(ContentType.JSON)
                .body(customerToPost)
                .post(baseUrl + customersPath);
        if (response.getStatusCode() != 201) {
            Assert.fail("Error posting Customer. Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }
}
