package com.agenticqa.apis;

import com.agenticqa.api.Api;
import com.agenticqa.config.Config;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;

public class CustomerApi implements Api {
    private final String baseUrl;
    private final String customersPath;

    public CustomerApi() {
        this.baseUrl = Config.getApiUrl("customers");
        this.customersPath = "/customers";
    }

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

    public Response getCustomer(long id) {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .get(baseUrl + customersPath + "/" + id);
        if (response.getStatusCode() != 200) {
            Assert.fail("Error getting Customer " + id + ". Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }

    public Response getCustomers() {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .get(baseUrl + customersPath);
        if (response.getStatusCode() != 200) {
            Assert.fail("Error getting Customers. Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }

    public Response putCustomer(long id, Object customerToPut) {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .contentType(ContentType.JSON)
                .body(customerToPut)
                .put(baseUrl + customersPath + "/" + id);
        if (response.getStatusCode() != 200) {
            Assert.fail("Error updating Customer " + id + ". Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }

    public Response deleteCustomer(long id) {
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .delete(baseUrl + customersPath + "/" + id);
        if (response.getStatusCode() != 204) {
            Assert.fail("Error deleting Customer " + id + ". Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }
}
