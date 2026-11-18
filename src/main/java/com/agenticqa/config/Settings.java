package com.agenticqa.config;

import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The run's settings: config/common.yaml with config/env/NAME.yaml on top. Read with {@code EnvConfig.get()}.
 */
public record Settings(Ui ui, Map<String, Service> services, Execution execution, Cleanup cleanup,
                       Healing healing, Retry retry, Jks jks, Url url, Sct sct) {

    /** Base URL of a service, {@code services.<name>.baseUrl}. */
    public String apiUrl(String name) {
        Service service = services == null ? null : services.get(name);
        if (service == null || service.baseUrl() == null || service.baseUrl().isBlank()) {
            throw new IllegalArgumentException("services." + name + ".baseUrl is not set in config/env/"
                    + EnvConfig.envName() + ".yaml");
        }
        return service.baseUrl();
    }

    /** Browser settings. */
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

    /** One HTTP service. */
    public record Service(String baseUrl) {}

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

    /** Java keystore credentials. The password is masked when printed. */
    public record Jks(String username, String password) {
        @Override
        public String toString() {
            return "Jks[username=" + username + ", password=****]";
        }
    }

    /** The url section of common.yaml. */
    public record Url(String mark, String klmhh) {}

    /** The sct section of the environment file. callps is masked when printed. */
    public record Sct(String cllid, String callps) {
        @Override
        public String toString() {
            return "Sct[cllid=" + cllid + ", callps=****]";
        }
    }
}
