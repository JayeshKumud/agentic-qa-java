package com.agenticqa.apis;

import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST Assured filter, registered once in {@code TestRunner}: attaches every request and its response to the
 * running Allure step as text.
 */
public class AllureReportLogFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Response response = context.next(request, responseSpec);
        String name = request.getMethod() + " " + request.getURI();
        Allure.addAttachment("Request: " + name, "text/plain", ApiCallText.request(request));
        Allure.addAttachment("Response: " + response.getStatusCode() + " " + name, "text/plain",
                ApiCallText.response(response));
        return response;
    }
}
