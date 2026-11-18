package com.agenticqa.config;

import com.agenticqa.config.Settings.Mode;
import com.agenticqa.core.BrowserPool;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class CommonConfigTest {

    @Test
    public void environmentFileWinsOverCommon() {
        assertEquals(Settings.getEnvironment(), "dev", "environment in common.properties");
        assertEquals(Settings.getBaseUrl(), "http://127.0.0.1:8000", "from dev.properties");
        assertFalse(Settings.isHeadless(), "dev.properties overrides common.properties");
        assertEquals(Settings.getBrowser(), "chromium", "from common.properties");
        assertEquals(Settings.getNavigationMs(), 30000);
        assertTrue(Settings.getCleanupFolders().contains("target/reports"));
        assertEquals(Settings.getVideo(), "failed");
        assertFalse(Settings.isIgnoreHttpsErrors());
        assertFalse(Settings.isMaximize());
    }

    @Test
    public void readsAnyKeyByName() {
        assertEquals(Settings.getJksUsername(), "system");
        assertEquals(Settings.getJksPassword(), "system1");
        assertEquals(Settings.get("url.feature"), "kl-mmknbj-cd", "from common.properties");
        assertEquals(Settings.get("url.klmhh"), "jhshdajh");
        assertEquals(Settings.get("cloud"), "testcould");
        assertEquals(Settings.get("sct.cllid"), "test", "from dev.properties");
        assertEquals(Settings.get("sct.callps"), "tstps");
        assertEquals(Settings.get("not.there"), null);
        assertEquals(Settings.get("not.there", "fallback"), "fallback");
        expectThrows(IllegalStateException.class, () -> Settings.require("not.there"));
    }

    @Test
    public void apiUrlIsBuiltFromBaseUrlAndEnvironmentChunk() {
        assertEquals(Settings.getApiUrl("customers"), "https://customers-kt-5.test");
    }

    @Test
    public void systemPropertyWins() {
        System.setProperty("sct.cllid", "fromCommandLine");
        try {
            assertEquals(Settings.get("sct.cllid"), "fromCommandLine");
        } finally {
            System.clearProperty("sct.cllid");
        }
    }

    @Test
    public void sequentialTagWinsThenParallelThenDefault() {
        assertEquals(Settings.modeOf(List.of("@api"), "parallel"), Mode.PARALLEL);
        assertEquals(Settings.modeOf(List.of("@api"), "Sequential"), Mode.SEQUENTIAL);
        assertEquals(Settings.modeOf(List.of("@Parallel"), "sequential"), Mode.PARALLEL);
        assertEquals(Settings.modeOf(List.of("@Sequential"), "parallel"), Mode.SEQUENTIAL);
        assertEquals(Settings.modeOf(List.of("@Parallel", "@Sequential"), "parallel"), Mode.SEQUENTIAL);
        assertEquals(Settings.modeOf(List.of("@sequential"), "parallel"), Mode.SEQUENTIAL, "case does not matter");
        expectThrows(IllegalArgumentException.class, () -> Settings.modeOf(List.of(), "sometimes"));
    }

    @Test
    public void maximizeOnlyAppliesToHeadedRuns() {
        assertTrue(BrowserPool.maximized(true, false));
        assertFalse(BrowserPool.maximized(true, true), "headless keeps the viewport");
    }
}
