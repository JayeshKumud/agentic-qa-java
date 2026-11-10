package com.agenticqa.api;

import java.util.Map;

/**
 * One HTTP call made during a scenario. The session keeps them in order, and the report shows them when the
 * scenario fails, so a broken step in a long multi-service scenario can be traced to the exact request.
 *
 * @param method       HTTP verb
 * @param url          full URL
 * @param headers      request headers that were sent
 * @param requestBody  the request body, or null
 * @param status       HTTP status of the response
 * @param responseBody the response body text
 * @param durationMs   time until the response arrived
 */
public record ApiCall(String method, String url, Map<String, String> headers, String requestBody, int status,
                      String responseBody, long durationMs) {

    /** Longest body shown in the report; large payloads are cut so the report stays readable. */
    private static final int MAX_BODY = 4000;

    /**
     * A readable description for the report.
     *
     * @return the request line, status, and both bodies (cut to {@value #MAX_BODY} characters)
     */
    public String describe() {
        StringBuilder out = new StringBuilder()
                .append(method).append(' ').append(url).append(" -> ").append(status)
                .append(" (").append(durationMs).append(" ms)");
        if (requestBody != null) out.append(System.lineSeparator()).append("  > ").append(cut(requestBody));
        if (responseBody != null && !responseBody.isEmpty()) {
            out.append(System.lineSeparator()).append("  < ").append(cut(responseBody));
        }
        return out.toString();
    }

    private static String cut(String body) {
        String flat = body.replaceAll("\\s*\\R\\s*", " ");
        return flat.length() <= MAX_BODY ? flat
                : flat.substring(0, MAX_BODY) + " ... (" + (flat.length() - MAX_BODY) + " more characters)";
    }
}
