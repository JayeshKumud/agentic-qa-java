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
    public void serviceIsLookedUpByNameAndApiUrlReturnsItsBaseUrl() {
        Settings settings = new Settings(null, Map.of("customers", new Settings.Service("http://c")), null, null, null, null, null);
        assertEquals(settings.service("customers").baseUrl(), "http://c");
        expectThrows(IllegalArgumentException.class, () -> settings.service("orders"));
        assertEquals(settings.apiUrl("customers"), "http://c");
        expectThrows(IllegalArgumentException.class, () -> settings.apiUrl("orders"));
        assertEquals(EnvConfig.get().apiUrl("customers"), "http://127.0.0.1:8000", "dev: services.customers.baseUrl");
    }

    @Test
    public void jksIsReadFromTheEnvironmentFileAndItsPasswordIsMasked() {
        Settings.Jks jks = EnvConfig.get().jks(); // dev
        assertEquals(jks.username(), "system");
        assertEquals(jks.password(), "system1");
        assertFalse(jks.toString().contains("system1"), "password must not appear in logs");
        Settings none = new Settings(null, null, null, null, null, null, null);
        assertFalse(none.hasJks());
        expectThrows(IllegalStateException.class, none::jks);
    }

    @Test
    public void loadsCommonAndEnvironmentFilesTogether() {
        Settings settings = EnvConfig.get(); // dev: common.yaml + env/dev.yaml
        assertEquals(settings.ui().baseUrl(), "http://127.0.0.1:8000", "from the environment file");
        assertEquals(settings.ui().browser(), "chromium", "from common.yaml");
        assertTrue(settings.ui().waits().navigationMs() > 0);
        assertEquals(settings.service("customers").baseUrl(), "http://127.0.0.1:8000", "from the environment file");
        assertTrue(settings.cleanup().folders().contains("target/reports"));
        assertEquals(settings.ui().video(), "failed", "videos of failed scenarios by default");
        assertFalse(settings.ui().ignoreHttpsErrors());
        assertFalse(settings.ui().maximize());
    }

    @Test
    public void videoModeIsNormalizedCheckedAndDefaultsToFailed() {
        assertEquals(new Settings(null, null, null, null, null, null, null).ui().video(), "failed");
        Settings.Ui always = new Settings.Ui(null, null, true, 0, null, false, false, " Always ", null);
        assertEquals(always.video(), "always");
        assertTrue(always.recordsVideo());
        assertFalse(new Settings.Ui(null, null, true, 0, null, false, false, "off", null).recordsVideo());
        expectThrows(IllegalArgumentException.class,
                () -> new Settings.Ui(null, null, true, 0, null, false, false, "sometimes", null));
    }

    @Test
    public void maximizeOnlyAppliesToHeadedRuns() {
        assertTrue(com.agenticqa.core.BrowserPool.maximized(
                new Settings.Ui(null, null, false, 0, null, true, false, null, null)));
        assertFalse(com.agenticqa.core.BrowserPool.maximized(
                new Settings.Ui(null, null, true, 0, null, true, false, null, null)), "headless keeps the viewport");
    }
}
