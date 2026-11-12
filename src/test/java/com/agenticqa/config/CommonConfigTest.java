package com.agenticqa.config;

import com.agenticqa.config.Settings.Execution.Mode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class CommonConfigTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static ObjectNode yaml(String text) throws Exception {
        return (ObjectNode) YAML.readTree(text);
    }

    @Test
    public void environmentValuesWinAndSectionsMergeKeyByKey() throws Exception {
        ObjectNode common = yaml("""
                ui: { browser: chromium, headless: true, waits: { actionMs: 15000, navigationMs: 30000 } }
                cleanup: { folders: [a, b] }
                """);
        JsonNode env = yaml("""
                ui: { baseUrl: "http://env", headless: false, waits: { actionMs: 20000 } }
                cleanup: { folders: [c] }
                """);
        JsonNode merged = EnvConfig.merge(common, env);
        assertEquals(merged.at("/ui/browser").asText(), "chromium");
        assertEquals(merged.at("/ui/baseUrl").asText(), "http://env");
        assertFalse(merged.at("/ui/headless").asBoolean());
        assertEquals(merged.at("/ui/waits/actionMs").asInt(), 20000);
        assertEquals(merged.at("/ui/waits/navigationMs").asInt(), 30000);
        assertEquals(merged.at("/cleanup/folders").size(), 1, "lists are replaced, not appended");
    }

    @Test
    public void systemPropertiesOverrideAnyPath() throws Exception {
        ObjectNode tree = yaml("""
                ui: { headless: true, waits: { actionMs: 1 } }
                services: { customers: { baseUrl: a } }
                """);
        Properties props = new Properties();
        props.setProperty("ui.headless", "false");
        props.setProperty("ui.waits.actionMs", "30000");
        props.setProperty("services.customers.baseUrl", "http://other");
        props.setProperty("services.orders.baseUrl", "http://orders");
        props.setProperty("java.version", "17");   // not a settings section: ignored
        props.setProperty("ui", "x");              // no path: ignored
        EnvConfig.applyOverrides(tree, props);
        assertEquals(tree.at("/ui/headless").asText(), "false");
        assertEquals(tree.at("/ui/waits/actionMs").asText(), "30000");
        assertEquals(tree.at("/services/customers/baseUrl").asText(), "http://other");
        assertEquals(tree.at("/services/orders/baseUrl").asText(), "http://orders");
        assertFalse(tree.has("java"));
    }

    @Test
    public void sequentialTagWinsThenParallelThenDefault() {
        Settings.Execution parallel = new Settings.Execution("parallel");
        Settings.Execution sequential = new Settings.Execution("Sequential");
        assertEquals(parallel.modeOf(List.of("@api")), Mode.PARALLEL);
        assertEquals(sequential.modeOf(List.of("@api")), Mode.SEQUENTIAL);
        assertEquals(sequential.modeOf(List.of("@Parallel")), Mode.PARALLEL);
        assertEquals(parallel.modeOf(List.of("@Sequential")), Mode.SEQUENTIAL);
        assertEquals(parallel.modeOf(List.of("@Parallel", "@Sequential")), Mode.SEQUENTIAL);
        assertEquals(parallel.modeOf(List.of("@sequential")), Mode.SEQUENTIAL, "case does not matter");
        expectThrows(IllegalArgumentException.class, () -> new Settings.Execution("sometimes").modeOf(List.of()));
    }

    @Test
    public void serviceGetsDefaultHeadersTimeoutAndAuth() {
        Settings.Service defaults = new Settings.Service(null, null,
                Map.of("Accept", "application/json", "X-Team", "qa"), null);
        Settings.Auth auth = new Settings.Auth("oauth2", "http://token", "id", "s3cret", null, null, null, null);
        Settings settings = new Settings(null, new Settings.Api(null, 12000), defaults,
                Map.of("customers", new Settings.Service("http://c", null, Map.of("X-Team", "customers"), auth)),
                null, null, null, null, null);
        Settings.Service service = settings.service("customers");
        assertEquals(service.timeoutMs(), Integer.valueOf(12000));
        assertEquals(service.headers(), Map.of("Accept", "application/json", "X-Team", "customers"));
        assertEquals(service.auth().kind(), "oauth2");
        assertFalse(service.auth().toString().contains("s3cret"), "secrets are masked");
        expectThrows(IllegalArgumentException.class, () -> settings.service("orders"));
    }

    @Test
    public void loadsCommonAndEnvironmentFilesTogether() {
        Settings settings = EnvConfig.get(); // dev: common.yaml + env/dev.yaml
        assertEquals(settings.ui().baseUrl(), "http://127.0.0.1:8000", "from the environment file");
        assertEquals(settings.ui().browser(), "chromium", "from common.yaml");
        assertTrue(settings.ui().waits().navigationMs() > 0);
        assertTrue(settings.endpoints().containsKey("customers"), "endpoint catalog from common.yaml");
        assertEquals(settings.service("customers").headers().get("Accept"), "application/json");
        assertEquals(settings.service("customers").auth().kind(), "none");
        assertTrue(settings.cleanup().folders().contains("target/reports"));
    }
}
