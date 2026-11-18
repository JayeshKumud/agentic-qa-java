package com.agenticqa.apis;

import com.agenticqa.api.Api;
import com.agenticqa.config.EnvConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;

import java.time.format.DateTimeFormatter;

public class WebserviceAdapterValidationApi implements Api {
    private final String baseUrl;
    private final String defaultingPath;
    private final DateTimeFormatter formatter;

    public WebserviceAdapterValidationApi() {
        this.baseUrl = EnvConfig.get().apiUrl("appWebserviceAdapter");
        this.defaultingPath = "/sendNackHeartBeat";
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    public Response postDeclaration(Object declarationToPost) {
        // TODO: the replacement of this replaceFirst is cut off in the screenshot; copy it from the original class
        String finalUrl = baseUrl.replaceFirst("^https://", "https://");
        Response response = RestAssured.given()
                .header("Accept", "*/*")
                .contentType(ContentType.JSON)
                .body(declarationToPost)
                .post(finalUrl + defaultingPath);
        if (response.getStatusCode() != 200) {
            Assert.fail("Error posting Declaration. Status code: " + response.getStatusCode()
                    + " : " + response.getBody().asString());
        }
        return response;
    }
}
