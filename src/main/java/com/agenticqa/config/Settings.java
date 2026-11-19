package com.agenticqa.config;

import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Static access to the run's settings (config/common.properties with config/env/NAME.properties on top).
 * A -D system property with the same key wins, for example -Dui.headless=false.
 */
public final class Settings {

    private Settings() {}

    // ------------------------------------------------------------------ generic

    /** Value of a key, or null if it is not set. */
    public static String get(String key) {
        String value = System.getProperty(key);
        return value != null ? value.trim() : EnvConfig.properties().getProperty(key);
    }

    /** Value of a key, or the fallback if it is not set. */
    public static String get(String key, String fallback) {
        String value = get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Value of a key; fails if it is not set. */
    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is not set in config/common.properties or config/env/"
                    + EnvConfig.envName() + ".properties");
        }
        return value;
    }

    /** Integer value of a key, or the fallback if it is not set. */
    public static int getInt(String key, int fallback) {
        String value = get(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    /** Decimal value of a key, or the fallback if it is not set. */
    public static double getDouble(String key, double fallback) {
        String value = get(key);
        return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
    }

    /** true/false value of a key, or the fallback if it is not set. */
    public static boolean getBoolean(String key, boolean fallback) {
        String value = get(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    /** Comma separated value of a key as a list; empty if it is not set. */
    public static List<String> getList(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // ------------------------------------------------------------------ environment and APIs

    /** The environment that runs, for example dev. */
    public static String getEnvironment() {
        return EnvConfig.envName();
    }

    /** URL of an API: https:// + apiName + - + environmentChunk + baseUrl, for example https://customers-kt-5.test */
    public static String getApiUrl(String apiName) {
        return "https://" + apiName + "-" + require("environmentChunk") + require("baseUrl");
    }

    // ------------------------------------------------------------------ browser

    public static String getBaseUrl()           { return get("ui.baseUrl"); }
    public static String getKlmUrl()            { return require("gui.klm.url"); }
    public static String getGsnUrl()            { return require("gui.gsn.url"); }
    public static int getGuiOpenTimeoutMs()     { return getInt("gui.openTimeoutMs", 30000); }
    public static int getLoginInputTimeoutMs()  { return getInt("gui.loginInputTimeoutMs", 15000); }
    public static String getBrowser()           { return get("ui.browser", "chromium"); }
    public static boolean isHeadless()          { return getBoolean("ui.headless", true); }
    public static int getSlowMoMs()             { return getInt("ui.slowMoMs", 0); }
    public static int getViewportWidth()        { return getInt("ui.viewport.width", 1366); }
    public static int getViewportHeight()       { return getInt("ui.viewport.height", 768); }
    public static boolean isMaximize()          { return getBoolean("ui.maximize", false); }
    public static boolean isIgnoreHttpsErrors() { return getBoolean("ui.ignoreHttpsErrors", false); }
    public static String getVideo()             { return get("ui.video", "failed"); }
    public static int getActionMs()             { return getInt("ui.waits.actionMs", 15000); }
    public static int getNavigationMs()         { return getInt("ui.waits.navigationMs", 30000); }
    public static int getAssertionMs()          { return getInt("ui.waits.assertionMs", 15000); }

    /** True unless ui.video is off. */
    public static boolean recordsVideo() {
        return !"off".equalsIgnoreCase(getVideo());
    }

    /** True if ui.video is always, so the video of a passed scenario is kept too. */
    public static boolean keepsAllVideos() {
        return "always".equalsIgnoreCase(getVideo());
    }

    // ------------------------------------------------------------------ execution

    public static final String PARALLEL_TAG = "@Parallel";
    public static final String SEQUENTIAL_TAG = "@Sequential";

    /** How one scenario runs. */
    public enum Mode { PARALLEL, SEQUENTIAL }

    public static String getDefaultMode() { return get("execution.defaultMode", "parallel"); }

    /** Mode of a scenario: @Sequential wins, then @Parallel, then execution.defaultMode. */
    public static Mode modeOf(Collection<String> tags) {
        return modeOf(tags, getDefaultMode());
    }

    /** Mode of a scenario with the given default mode. */
    public static Mode modeOf(Collection<String> tags, String defaultMode) {
        if (tags.stream().anyMatch(SEQUENTIAL_TAG::equalsIgnoreCase)) return Mode.SEQUENTIAL;
        if (tags.stream().anyMatch(PARALLEL_TAG::equalsIgnoreCase)) return Mode.PARALLEL;
        return switch (defaultMode.trim().toLowerCase()) {
            case "parallel" -> Mode.PARALLEL;
            case "sequential" -> Mode.SEQUENTIAL;
            default -> throw new IllegalArgumentException(
                    "execution.defaultMode must be parallel or sequential, not '" + defaultMode + "'");
        };
    }

    // ------------------------------------------------------------------ cleanup

    public static boolean isCleanupBeforeRun()     { return getBoolean("cleanup.beforeRun", false); }
    public static List<String> getCleanupFolders() { return getList("cleanup.folders"); }
    public static String getLogFile()              { return get("cleanup.logFile"); }

    // ------------------------------------------------------------------ healing

    public static boolean isHealingEnabled()    { return getBoolean("healing.enabled", true); }
    public static int getProbeTimeoutMs()       { return getInt("healing.probeTimeoutMs", 2000); }
    public static double getMinScore()          { return getDouble("healing.minScore", 0.6); }
    public static String getFingerprintsFile()  { return get("healing.fingerprintsFile", "healing/fingerprints.json"); }
    public static String getHealingReportFile() { return get("healing.reportFile", "target/healing/heals.jsonl"); }

    // ------------------------------------------------------------------ retry

    public static int getRetryCount()   { return getInt("retry.count", 0); }
    public static String getRetryTags() { return get("retry.tags", ""); }

    /** True if a failed scenario with these tags may be retried. */
    public static boolean retryAppliesTo(Collection<String> scenarioTags) {
        return retryAppliesTo(scenarioTags, getRetryCount(), getRetryTags());
    }

    /** True if a scenario with these tags may be retried, for the given count and tag expression. */
    public static boolean retryAppliesTo(Collection<String> scenarioTags, int count, String tags) {
        if (count <= 0) return false;
        if (tags == null || tags.isBlank()) return true;
        return TagExpressionParser.parse(tags).evaluate(List.copyOf(scenarioTags));
    }

    // ------------------------------------------------------------------ credentials

    public static String getJksUsername() { return get("jks.username"); }
    public static String getJksPassword() { return get("jks.password"); }
}
