package com.agenticqa.apis;

import com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST Assured filter, registered once in {@code TestRunner}: logs every request and its response in the current
 * step of the Extent report. Outside a Cucumber step (no Extent step on this thread) it logs nothing.
 */
public class ExtentReportLogFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Response response = context.next(request, responseSpec);
        if (ExtentCucumberAdapter.getCurrentStep() != null) {
            ExtentCucumberAdapter.addTestStepLog("<pre>" + escape(ApiCallText.request(request)) + "\n\n"
                    + escape(ApiCallText.response(response)) + "</pre>");
        }
        return response;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
