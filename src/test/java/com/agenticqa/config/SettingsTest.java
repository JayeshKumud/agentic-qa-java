package com.agenticqa.config;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SettingsTest {

    private static final Map<String, String> VARS = Map.of("UAT_URL", "https://uat.example", "EMPTY", "");

    @Test
    public void fillsPlaceholdersAndFallbacks() {
        assertEquals(EnvConfig.fill("${UAT_URL}/app", VARS::get), "https://uat.example/app");
        assertEquals(EnvConfig.fill("${MISSING:-http://localhost:8000}", VARS::get), "http://localhost:8000");
        assertEquals(EnvConfig.fill("${UAT_URL:-unused}", VARS::get), "https://uat.example");
        assertEquals(EnvConfig.fill("key=${EMPTY}", VARS::get), "key=");
        assertEquals(EnvConfig.fill("no placeholders", VARS::get), "no placeholders");
    }

    @Test
    public void failsOnAPlaceholderWithoutValue() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> EnvConfig.fill("${API_KEY}", VARS::get));
        assertTrue(e.getMessage().contains("API_KEY"), e.getMessage());
    }

    @Test
    public void retryAppliesByTagExpression() {
        Settings.Retry retry = new Settings.Retry(2, "@ui and not @noretry");
        assertTrue(retry.appliesTo(List.of("@ui", "@smoke")));
        assertFalse(retry.appliesTo(List.of("@ui", "@noretry")));
        assertFalse(retry.appliesTo(List.of("@api")));
        assertTrue(new Settings.Retry(1, "").appliesTo(List.of()));
        assertFalse(new Settings.Retry(0, "").appliesTo(List.of("@ui")));
    }
}
