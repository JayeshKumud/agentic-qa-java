package com.agenticqa.api;

import com.agenticqa.config.Settings.Auth;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ServiceAuthTest {

    @Test
    public void fetchesAnOauthTokenOnceAndReusesIt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] json = "{\"access_token\":\"tok-123\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            ServiceAuth.clearTokens();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
            Auth auth = new Auth("oauth2", url, "my-client", "my secret", "read write", null, null, null);
            assertEquals(ServiceAuth.headers("svc", auth), Map.of("Authorization", "Bearer tok-123"));
            assertEquals(ServiceAuth.headers("svc", auth), Map.of("Authorization", "Bearer tok-123"));
            assertEquals(calls.get(), 1, "the token is cached");
            String form = bodies.get(0);
            assertTrue(form.contains("grant_type=client_credentials"), form);
            assertTrue(form.contains("client_id=my-client"), form);
            assertTrue(form.contains("client_secret=my+secret"), form);
            assertTrue(form.contains("scope=read+write"), form);
        } finally {
            server.stop(0);
            ServiceAuth.clearTokens();
        }
    }

    @Test
    public void namesTheMissingCredential() {
        Auth auth = new Auth("oauth2", "http://x/token", "id", " ", null, null, null, null);
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> ServiceAuth.headers("orders", auth));
        assertTrue(e.getMessage().contains("orders") && e.getMessage().contains("clientSecret"), e.getMessage());
        expectThrows(IllegalStateException.class,
                () -> ServiceAuth.headers("orders", new Auth("kerberos", null, null, null, null, null, null, null)));
    }

    @Test
    public void basicAndBearerHeaders() {
        Auth basic = new Auth("basic", null, null, null, null, "u", "p", null);
        assertEquals(ServiceAuth.headers("s", basic).get("Authorization"), "Basic dTpw");
        Auth bearer = new Auth("bearer", null, null, null, null, null, null, "t");
        assertEquals(ServiceAuth.headers("s", bearer).get("Authorization"), "Bearer t");
        assertEquals(ServiceAuth.headers("s", null), Map.of());
    }

    @Test
    public void masksSecretHeadersForTheCallLog() {
        Map<String, String> masked = ServiceAuth.mask(Map.of("Authorization", "Bearer abc", "Accept", "json"));
        assertEquals(masked.get("Authorization"), "****");
        assertEquals(masked.get("Accept"), "json");
    }
}
