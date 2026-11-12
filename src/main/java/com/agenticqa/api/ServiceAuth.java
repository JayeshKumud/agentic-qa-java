package com.agenticqa.api;

import com.agenticqa.config.Settings.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a service's {@code auth} settings (client id, secret, ...) into the Authorization header of its requests:
 * <ul>
 *   <li>{@code oauth2}: OAuth2 client credentials grant. The token is fetched from {@code tokenUrl} the first time
 *       any scenario calls the service, shared by all threads of the run, and fetched again 60 s before it
 *       expires.</li>
 *   <li>{@code basic}: {@code Authorization: Basic base64(username:password)}.</li>
 *   <li>{@code bearer}: a fixed token, {@code Authorization: Bearer token}.</li>
 *   <li>{@code none} (default): no header.</li>
 * </ul>
 * The token request goes through Java's own HTTP client, not the browser context, so a token is not tied to one
 * scenario. Secrets are never logged: {@link #mask} hides them in the API call log of the report.
 */
public final class ServiceAuth {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Duration RENEW_BEFORE = Duration.ofSeconds(60);
    private static final Map<String, Token> TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final List<String> SECRET_HEADERS = List.of("authorization", "proxy-authorization", "cookie",
            "x-api-key", "api-key");

    /** Utility class, not instantiable. */
    private ServiceAuth() {}

    private record Token(String value, Instant renewAt) {}

    /**
     * The headers that authenticate a request.
     *
     * @param service the service name, for error messages
     * @param auth    the service's auth settings, may be null
     * @return the Authorization header, or nothing for type none
     * @throws IllegalStateException if a required value is missing, the type is unknown, or no token is issued
     */
    public static Map<String, String> headers(String service, Auth auth) {
        if (auth == null) return Map.of();
        return switch (auth.kind()) {
            case "none" -> Map.of();
            case "bearer" -> Map.of("Authorization", "Bearer " + require(service, "token", auth.token()));
            case "basic" -> {
                String pair = require(service, "username", auth.username()) + ":"
                        + require(service, "password", auth.password());
                yield Map.of("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
            }
            case "oauth2" -> Map.of("Authorization", "Bearer " + oauthToken(service, auth));
            default -> throw new IllegalStateException("Service '" + service + "': unknown auth type '" + auth.type()
                    + "'. Use none, oauth2, basic or bearer.");
        };
    }

    /**
     * A copy of request headers that is safe to log: values of Authorization, Cookie and API key headers are
     * replaced by {@code ****}.
     *
     * @param headers the headers that were sent
     * @return the masked copy
     */
    public static Map<String, String> mask(Map<String, String> headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((name, value) -> masked.put(name,
                SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? "****" : value));
        return masked;
    }

    /** Forgets every cached token, so the next call fetches a new one (for example after a 401). */
    public static void clearTokens() {
        TOKENS.clear();
    }

    private static String oauthToken(String service, Auth auth) {
        String tokenUrl = require(service, "tokenUrl", auth.tokenUrl());
        String clientId = require(service, "clientId", auth.clientId());
        String secret = require(service, "clientSecret", auth.clientSecret());
        String key = tokenUrl + "|" + clientId + "|" + auth.scope();
        Token token = TOKENS.get(key);
        if (token != null && Instant.now().isBefore(token.renewAt())) return token.value();
        synchronized (LOCKS.computeIfAbsent(key, k -> new Object())) {
            token = TOKENS.get(key);
            if (token != null && Instant.now().isBefore(token.renewAt())) return token.value();
            token = fetch(service, tokenUrl, clientId, secret, auth.scope());
            TOKENS.put(key, token);
            return token.value();
        }
    }

    private static Token fetch(String service, String tokenUrl, String clientId, String secret, String scope) {
        List<String> form = new ArrayList<>(List.of("grant_type=client_credentials",
                "client_id=" + encode(clientId), "client_secret=" + encode(secret)));
        if (scope != null && !scope.isBlank()) form.add("scope=" + encode(scope.trim()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Service '" + service + "': token request to " + tokenUrl
                        + " failed with status " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = MAPPER.readTree(response.body());
            String value = json.path("access_token").asText(null);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Service '" + service + "': " + tokenUrl
                        + " answered without access_token");
            }
            long seconds = json.path("expires_in").asLong(3600);
            Instant renewAt = Instant.now().plusSeconds(seconds).minus(RENEW_BEFORE);
            System.out.printf("[AUTH] token for service '%s' (client %s) issued, valid %d s%n", service, clientId,
                    seconds);
            return new Token(value, renewAt);
        } catch (IOException e) {
            throw new UncheckedIOException("Service '" + service + "': cannot reach token endpoint " + tokenUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching a token for service '" + service + "'", e);
        }
    }

    private static String require(String service, String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Service '" + service + "' uses auth but has no " + field
                    + ". Set it under services." + service + ".auth in the environment file (secrets as ${NAME}).");
        }
        return value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
