package com.agenticqa.apis;

import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;

import java.util.Locale;
import java.util.Set;

/** Request and response as readable text, for the report filters. Secrets in headers are masked. */
final class ApiCallText {

    private static final Set<String> SECRET_HEADERS = Set.of("authorization", "cookie", "x-api-key");
    private static final int MAX_BODY = 10000;

    private ApiCallText() {}

    static String request(FilterableRequestSpecification request) {
        StringBuilder text = new StringBuilder(request.getMethod()).append(' ').append(request.getURI()).append('\n');
        for (Header header : request.getHeaders()) text.append(header(header)).append('\n');
        Object body = request.getBody();
        if (body != null) text.append('\n').append(cut(String.valueOf(body)));
        return text.toString();
    }

    static String response(Response response) {
        StringBuilder text = new StringBuilder(response.getStatusLine()).append('\n');
        for (Header header : response.getHeaders()) text.append(header(header)).append('\n');
        String body = response.asString();
        if (!body.isEmpty()) text.append('\n').append(cut(body));
        return text.toString();
    }

    private static String header(Header header) {
        boolean secret = SECRET_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT));
        return header.getName() + ": " + (secret ? "****" : header.getValue());
    }

    private static String cut(String body) {
        return body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "... (cut)" : body;
    }
}
