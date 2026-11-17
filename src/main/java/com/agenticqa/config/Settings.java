package com.agenticqa.config;

import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Typed view of the run's settings: config/common.yaml with config/env/NAME.yaml merged on top (see
 * {@link EnvConfig}). Every value can be overridden for one run with a system property named by its path, for
 * example {@code -Dui.headless=false} or {@code -Dservices.customers.baseUrl=http://...}.
 *
 * @param ui              browser settings and waits
 * @param services        the HTTP services tests talk to, by name, for example "customers" ({@link Config#getApiUrl})
 * @param execution       how scenarios without @Parallel or @Sequential run
 * @param cleanup         folders emptied before a run, and the run's log file
 * @param healing         self-healing locator settings; defaults apply when the section is missing
 * @param retry           which failed scenarios are run again, and how often; no retries when the section is missing
 */
public record Settings(Ui ui, Map<String, Service> services, Execution execution, Cleanup cleanup,
                       Healing healing, Retry retry) {

    /**
     * Browser settings, never null, with defaults for missing values.
     *
     * @return the ui section
     */
    @Override
    public Ui ui() {
        Ui base = ui != null ? ui : new Ui(null, null, true, 0, null, false, false, null, null);
        return new Ui(base.baseUrl(), base.browser() == null ? "chromium" : base.browser(), base.headless(),
                base.slowMoMs(), base.viewport() != null ? base.viewport() : new Viewport(1366, 768),
                base.maximize(), base.ignoreHttpsErrors(), base.video() == null ? "failed" : base.video(),
                base.waits() != null ? base.waits() : Waits.defaults());
    }

    /**
     * Retry settings, never null.
     *
     * @return the retry section, or no retries
     */
    @Override
    public Retry retry() {
        Retry base = retry != null ? retry : Retry.none();
        return new Retry(base.count(), base.tags() == null ? "" : base.tags());
    }

    /**
     * Execution settings, never null.
     *
     * @return the execution section, or parallel by default
     */
    @Override
    public Execution execution() {
        return execution != null && execution.defaultMode() != null ? execution : new Execution("parallel");
    }

    /**
     * Cleanup settings, never null.
     *
     * @return the cleanup section, or no cleanup and no log file
     */
    @Override
    public Cleanup cleanup() {
        if (cleanup == null) return new Cleanup(false, List.of(), null);
        return new Cleanup(cleanup.beforeRun(), cleanup.folders() == null ? List.of() : List.copyOf(cleanup.folders()),
                cleanup.logFile());
    }

    /**
     * Settings of one named service.
     *
     * @param name the service name
     * @return the service settings
     * @throws IllegalArgumentException if no service with that name is configured
     */
    public Service service(String name) {
        Service service = services == null ? null : services.get(name);
        if (service == null) {
            throw new IllegalArgumentException("Unknown service '" + name + "'. Configured services: "
                    + (services == null ? "none" : new TreeMap<>(services).keySet())
                    + ". Add it under 'services:' in config/env/" + EnvConfig.envName() + ".yaml");
        }
        return service;
    }

    /**
     * Healing settings, never null.
     *
     * @return the configured section, or {@link Healing#defaults()} if there is none
     */
    @Override
    public Healing healing() {
        return healing != null ? healing : Healing.defaults();
    }

    /**
     * Browser and GUI settings.
     *
     * @param baseUrl  application URL; relative paths such as "/" resolve against it
     * @param browser  chromium, firefox or webkit
     * @param headless run without a visible browser window
     * @param slowMoMs artificial delay per action in milliseconds, useful when debugging
     * @param viewport          browser window size, used unless {@code maximize} is on
     * @param maximize          use the real window size instead of the viewport (maximized window); headed runs only
     * @param ignoreHttpsErrors accept invalid HTTPS certificates, for environments with self-signed certificates
     * @param video             off, failed (keep videos of failed scenarios only) or always
     * @param waits             how long Playwright waits before it gives up
     */
    public record Ui(String baseUrl, String browser, boolean headless, int slowMoMs, Viewport viewport,
                     boolean maximize, boolean ignoreHttpsErrors, String video, Waits waits) {

        /** Normalizes and checks the video mode. */
        public Ui {
            if (video != null) {
                video = video.trim().toLowerCase(Locale.ROOT);
                if (!List.of("off", "failed", "always").contains(video)) {
                    throw new IllegalArgumentException("ui.video must be off, failed or always, not '" + video + "'");
                }
            }
        }

        /** @return true if the scenario's browser session is recorded */
        public boolean recordsVideo() {
            return video != null && !video.equals("off");
        }
    }

    /**
     * Browser window size.
     *
     * @param width  pixels
     * @param height pixels
     */
    public record Viewport(int width, int height) {}

    /**
     * Playwright wait times in milliseconds. A missing or zero value uses the default.
     *
     * @param actionMs     click, fill and locator waits
     * @param navigationMs page navigation and load
     * @param assertionMs  {@code assertThat(...)} web assertions
     */
    public record Waits(int actionMs, int navigationMs, int assertionMs) {

        /** Normalizes missing values. */
        public Waits {
            if (actionMs <= 0) actionMs = 15000;
            if (navigationMs <= 0) navigationMs = actionMs * 2;
            if (assertionMs <= 0) assertionMs = actionMs;
        }

        /**
         * Waits used when the file has none.
         *
         * @return 15 s actions and assertions, 30 s navigation
         */
        public static Waits defaults() {
            return new Waits(15000, 30000, 15000);
        }
    }

    /**
     * One HTTP service (microservice).
     *
     * @param baseUrl service URL, read by API classes with {@code Config.getApiUrl(name)}
     */
    public record Service(String baseUrl) {}

    /**
     * How scenarios run.
     *
     * @param defaultMode parallel or sequential, for scenarios tagged neither @Parallel nor @Sequential
     */
    public record Execution(String defaultMode) {

        /** Tag that makes a feature or scenario run in parallel with the others. */
        public static final String PARALLEL_TAG = "@Parallel";
        /** Tag that makes a feature or scenario run alone; wins over {@link #PARALLEL_TAG}. */
        public static final String SEQUENTIAL_TAG = "@Sequential";

        /** How one scenario runs. */
        public enum Mode { PARALLEL, SEQUENTIAL }

        /**
         * Chooses the mode of a scenario from its tags (including those inherited from the feature). Tags are
         * compared ignoring case. Pure logic, unit tested.
         *
         * @param tags the scenario's tags
         * @return SEQUENTIAL if tagged @Sequential, else PARALLEL if tagged @Parallel, else the default mode
         * @throws IllegalArgumentException if defaultMode is neither parallel nor sequential
         */
        public Mode modeOf(Collection<String> tags) {
            if (tags.stream().anyMatch(SEQUENTIAL_TAG::equalsIgnoreCase)) return Mode.SEQUENTIAL;
            if (tags.stream().anyMatch(PARALLEL_TAG::equalsIgnoreCase)) return Mode.PARALLEL;
            return switch (defaultMode.trim().toLowerCase(Locale.ROOT)) {
                case "parallel" -> Mode.PARALLEL;
                case "sequential" -> Mode.SEQUENTIAL;
                default -> throw new IllegalArgumentException("execution.defaultMode must be parallel or "
                        + "sequential, not '" + defaultMode + "'");
            };
        }
    }

    /**
     * What is removed before a run, so logs and reports show only the new run.
     *
     * @param beforeRun false keeps everything from earlier runs
     * @param folders   folders emptied before the run, relative to the project folder
     * @param logFile   file that receives a copy of everything printed during the run; null for none
     */
    public record Cleanup(boolean beforeRun, List<String> folders, String logFile) {}

    /**
     * Self-healing locator settings.
     *
     * @param enabled          false makes every locator behave exactly like a plain Playwright locator
     * @param probeTimeoutMs   how long the original selector may take to appear before healing starts
     * @param minScore         lowest similarity (0..1) accepted for a replacement element
     * @param fingerprintsFile JSON file with the last known fingerprint of every element (keep it under version
     *                         control: it is the baseline healing compares against)
     * @param reportFile       JSON-lines file; one line is appended per heal
     */
    public record Healing(boolean enabled, int probeTimeoutMs, double minScore,
                          String fingerprintsFile, String reportFile) {

        /**
         * Settings used when there is no healing section.
         *
         * @return enabled, 2 s probe, score 0.6, default file locations
         */
        public static Healing defaults() {
            return new Healing(true, 2000, 0.6, "healing/fingerprints.json", "target/healing/heals.jsonl");
        }
    }

    /**
     * Retry of failed scenarios. Only the scenario that failed runs again, immediately, in a new session (new
     * browser context, new {@code {unique}} token); passed scenarios never run twice.
     *
     * @param count how often a failed scenario may run again; 0 switches retries off
     * @param tags  Cucumber tag expression choosing which scenarios may be retried, for example
     *              {@code "@api and not @noretry"}; empty means every scenario
     */
    public record Retry(int count, String tags) {

        /**
         * No retries.
         *
         * @return count 0
         */
        public static Retry none() {
            return new Retry(0, "");
        }

        /**
         * Tells whether a scenario with these tags may be retried.
         *
         * @param scenarioTags the scenario's tags, including those inherited from the feature
         * @return true if retries are on and the tag expression matches
         * @throws IllegalArgumentException if the tag expression is malformed
         */
        public boolean appliesTo(Collection<String> scenarioTags) {
            if (count <= 0) return false;
            if (tags == null || tags.isBlank()) return true;
            Expression expression = TagExpressionParser.parse(tags);
            return expression.evaluate(List.copyOf(scenarioTags));
        }
    }
}
