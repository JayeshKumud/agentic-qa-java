package com.agenticqa.config;

import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Collection;
import java.util.List;

/**
 * The run's settings: config/common.yaml with config/env/NAME.yaml on top. Read with {@code EnvConfig.get()}.
 */
public record Settings(Ui ui, Execution execution, Cleanup cleanup,
                       Healing healing, Retry retry, Jks jks, Url url, Sct sct, String cloud, String environment,
                       String baseUrl, String environmentChunk) {

    /** URL of an API: https:// + apiName + - + environmentChunk + baseUrl, for example https://customers-kt-5.test */
    public String getApiUrl(String apiName) {
        if (baseUrl == null || environmentChunk == null) {
            throw new IllegalStateException("baseUrl and environmentChunk must be set in config/env/"
                    + EnvConfig.envName() + ".yaml");
        }
        return "https://" + apiName + "-" + environmentChunk + baseUrl;
    }

    /** Browser settings; baseUrl is also the base URL of the API classes. */
    public record Ui(String baseUrl, String browser, boolean headless, int slowMoMs, Viewport viewport,
                     boolean maximize, boolean ignoreHttpsErrors, String video, Waits waits) {

        /** True unless video is off. */
        public boolean recordsVideo() {
            return !"off".equalsIgnoreCase(video);
        }

        /** True if the video of a passed scenario is kept too. */
        public boolean keepsAllVideos() {
            return "always".equalsIgnoreCase(video);
        }
    }

    /** Browser window size in pixels. */
    public record Viewport(int width, int height) {}

    /** Playwright wait times in milliseconds. */
    public record Waits(int actionMs, int navigationMs, int assertionMs) {}

    /** How scenarios without @Parallel or @Sequential run: parallel or sequential. */
    public record Execution(String defaultMode) {

        public static final String PARALLEL_TAG = "@Parallel";
        public static final String SEQUENTIAL_TAG = "@Sequential";

        public enum Mode { PARALLEL, SEQUENTIAL }

        /** Mode of a scenario: @Sequential wins, then @Parallel, then defaultMode. */
        public Mode modeOf(Collection<String> tags) {
            if (tags.stream().anyMatch(SEQUENTIAL_TAG::equalsIgnoreCase)) return Mode.SEQUENTIAL;
            if (tags.stream().anyMatch(PARALLEL_TAG::equalsIgnoreCase)) return Mode.PARALLEL;
            return switch (defaultMode.trim().toLowerCase()) {
                case "parallel" -> Mode.PARALLEL;
                case "sequential" -> Mode.SEQUENTIAL;
                default -> throw new IllegalArgumentException(
                        "execution.defaultMode must be parallel or sequential, not '" + defaultMode + "'");
            };
        }
    }

    /** Folders emptied before a run, and the run's log file. */
    public record Cleanup(boolean beforeRun, List<String> folders, String logFile) {}

    /** Self-healing locator settings. */
    public record Healing(boolean enabled, int probeTimeoutMs, double minScore,
                          String fingerprintsFile, String reportFile) {}

    /** Retry of failed scenarios: how often, and which ones (a Cucumber tag expression, empty for all). */
    public record Retry(int count, String tags) {

        /** True if a scenario with these tags may be retried. */
        public boolean appliesTo(Collection<String> scenarioTags) {
            if (count <= 0) return false;
            if (tags == null || tags.isBlank()) return true;
            return TagExpressionParser.parse(tags).evaluate(List.copyOf(scenarioTags));
        }
    }

    /** Java keystore credentials. */
    public record Jks(String username, String password) {}

    /** The url section of common.yaml. */
    public record Url(String feature, String klmhh) {}

    /** The sct section of the environment file. */
    public record Sct(String cllid, String callps) {}
}
