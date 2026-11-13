package com.agenticqa.config;

import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Collection;
import java.util.LinkedHashMap;
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
 * @param api             REST API settings
 * @param serviceDefaults headers (and timeout) every service gets unless it sets its own; may be null
 * @param services        the HTTP services (microservices) tests talk to, by name, for example "customers"
 * @param endpoints       the endpoint catalog, by group name (the part before the dot in "customers.get")
 * @param execution       how scenarios without @Parallel or @Sequential run
 * @param cleanup         folders emptied before a run, and the run's log file
 * @param healing         self-healing locator settings; defaults apply when the section is missing
 * @param retry           which failed scenarios are run again, and how often; no retries when the section is missing
 */
public record Settings(Ui ui, Api api, Service serviceDefaults, Map<String, Service> services,
                       Map<String, EndpointGroup> endpoints, Execution execution, Cleanup cleanup,
                       Healing healing, Retry retry) {

    /**
     * Browser settings, never null, with defaults for missing values.
     *
     * @return the ui section
     */
    @Override
    public Ui ui() {
        Ui base = ui != null ? ui : new Ui(null, null, true, 0, null, null);
        return new Ui(base.baseUrl(), base.browser() == null ? "chromium" : base.browser(), base.headless(),
                base.slowMoMs(), base.viewport() != null ? base.viewport() : new Viewport(1366, 768),
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
     * The endpoint catalog, never null.
     *
     * @return group name to group
     */
    @Override
    public Map<String, EndpointGroup> endpoints() {
        return endpoints == null ? Map.of() : endpoints;
    }

    /**
     * Settings of one named service: its own values, else {@code serviceDefaults}, else {@code api}.
     * Headers are merged: the service's headers win over the default headers.
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
        Service defaults = serviceDefaults != null ? serviceDefaults : new Service(null, null, null, null);
        Integer timeout = service.timeoutMs() != null ? service.timeoutMs()
                : defaults.timeoutMs() != null ? defaults.timeoutMs()
                : api != null && api.timeoutMs() > 0 ? api.timeoutMs() : 15000;
        Map<String, String> headers = new LinkedHashMap<>();
        if (defaults.headers() != null) headers.putAll(defaults.headers());
        if (service.headers() != null) headers.putAll(service.headers());
        Auth auth = service.auth() != null ? service.auth() : defaults.auth() != null ? defaults.auth() : Auth.none();
        return new Service(service.baseUrl(), timeout, Map.copyOf(headers), auth);
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
     * @param viewport browser window size
     * @param waits    how long Playwright waits before it gives up
     */
    public record Ui(String baseUrl, String browser, boolean headless, int slowMoMs, Viewport viewport, Waits waits) {}

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
     * REST API settings.
     *
     * @param baseUrl   API URL
     * @param timeoutMs default timeout of one request in milliseconds, for services without their own
     */
    public record Api(String baseUrl, int timeoutMs) {}

    /**
     * One HTTP service (microservice).
     *
     * @param baseUrl   service URL; request paths such as "/orders" resolve against it
     * @param timeoutMs timeout of one request in milliseconds; null uses serviceDefaults, then api.timeoutMs
     * @param headers   headers sent with every request to this service; may be null
     * @param auth      how requests to this service log in; null means none
     */
    public record Service(String baseUrl, Integer timeoutMs, Map<String, String> headers, Auth auth) {}

    /**
     * How requests to a service authenticate. Secrets come from placeholders ({@code ${UAT_CLIENT_SECRET}}),
     * never from committed files, and {@link #toString()} masks them, so they never reach a log or report.
     *
     * @param type         none, oauth2 (client credentials grant), basic or bearer
     * @param tokenUrl     oauth2: the token endpoint
     * @param clientId     oauth2: the client id
     * @param clientSecret oauth2: the client secret
     * @param scope        oauth2: optional, space separated scopes
     * @param username     basic: the user
     * @param password     basic: the password
     * @param token        bearer: a fixed token
     */
    public record Auth(String type, String tokenUrl, String clientId, String clientSecret, String scope,
                       String username, String password, String token) {

        /**
         * No authentication.
         *
         * @return type none
         */
        public static Auth none() {
            return new Auth("none", null, null, null, null, null, null, null);
        }

        /**
         * The type, normalized.
         *
         * @return none, oauth2, basic or bearer, lower case
         */
        public String kind() {
            return type == null || type.isBlank() ? "none" : type.trim().toLowerCase(Locale.ROOT);
        }

        /** @return the settings with secrets masked */
        @Override
        public String toString() {
            return "Auth[type=" + kind() + ", tokenUrl=" + tokenUrl + ", clientId=" + clientId
                    + ", clientSecret=" + mask(clientSecret) + ", scope=" + scope + ", username=" + username
                    + ", password=" + mask(password) + ", token=" + mask(token) + "]";
        }

        private static String mask(String secret) {
            return secret == null || secret.isEmpty() ? String.valueOf(secret) : "****";
        }
    }

    /**
     * One group of the endpoint catalog.
     *
     * @param service   the service name under 'services:'
     * @param endpoints endpoint key to endpoint, for example "get"
     */
    public record EndpointGroup(String service, Map<String, EndpointSpec> endpoints) {}

    /**
     * One endpoint as written in the catalog.
     *
     * @param method HTTP verb
     * @param path   path template, for example /customers/{id}
     * @param body   default body file under src/test/resources/testdata, or null
     */
    public record EndpointSpec(String method, String path, String body) {}

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
