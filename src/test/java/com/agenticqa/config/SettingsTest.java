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
    public void environmentNameLosesSurroundingQuotes() {
        assertEquals(EnvConfig.unquote("\"dev\""), "dev");
        assertEquals(EnvConfig.unquote(" 'uat' "), "uat");
        assertEquals(EnvConfig.unquote("dev"), "dev");
        assertEquals(EnvConfig.unquote(null), "");
    }

    @Test
    public void retryAppliesByTagExpression() {
        String tags = "@ui and not @noretry";
        assertTrue(Settings.retryAppliesTo(List.of("@ui", "@smoke"), 2, tags));
        assertFalse(Settings.retryAppliesTo(List.of("@ui", "@noretry"), 2, tags));
        assertFalse(Settings.retryAppliesTo(List.of("@api"), 2, tags));
        assertTrue(Settings.retryAppliesTo(List.of(), 1, ""));
        assertFalse(Settings.retryAppliesTo(List.of("@ui"), 0, ""));
    }
}
