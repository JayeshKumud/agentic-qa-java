package com.agenticqa.config;

import java.util.Map;
import java.util.TreeMap;

/**
 * Typed view of one environment file (config/env/NAME.yaml).
 * Kept minimal on purpose: new sections (healing, reporting, ...) are added in the step that needs them.
 *
 * @param ui      browser and GUI settings
 * @param api     REST API settings
 * @param healing  self-healing locator settings; defaults apply when the section is missing
 * @param services the HTTP services (microservices) tests talk to, by name, for example "customers" or "orders"
 */
public record Settings(Ui ui, Api api, Healing healing, Map<String, Service> services) {

    /**
     * Settings of one named service. The base URL can be overridden per run with
     * {@code -Dservices.NAME.baseUrl=http://...}, for example to point one service at another host.
     *
     * @param name the service name
     * @return the service settings; a missing timeout falls back to api.timeoutMs
     * @throws IllegalArgumentException if no service with that name is configured
     */
    public Service service(String name) {
        Service service = services == null ? null : services.get(name);
        if (service == null) {
            throw new IllegalArgumentException("Unknown service '" + name + "'. Configured services: "
                    + (services == null ? "none" : new TreeMap<>(services).keySet())
                    + ". Add it under 'services:' in config/env/" + EnvConfig.envName() + ".yaml");
        }
        String override = System.getProperty("services." + name + ".baseUrl");
        String baseUrl = override == null || override.isBlank() ? service.baseUrl() : override.trim();
        int timeout = service.timeoutMs() != null ? service.timeoutMs() : (api != null ? api.timeoutMs() : 15000);
        return new Service(baseUrl, timeout, service.headers() == null ? Map.of() : Map.copyOf(service.headers()));
    }

    /**
     * Healing settings, never null.
     *
     * @return the configured section, or {@link Healing#defaults()} if the file has none
     */
    @Override
    public Healing healing() {
        return healing != null ? healing : Healing.defaults();
    }

    /**
     * Browser and GUI settings.
     *
     * @param baseUrl   application URL; relative paths such as "/" resolve against it
     * @param browser   chromium, firefox or webkit
     * @param headless  run without a visible browser window
     * @param slowMoMs  artificial delay per action in milliseconds, useful when debugging
     * @param timeoutMs default timeout for Playwright actions and assertions
     */
    public record Ui(String baseUrl, String browser, boolean headless, int slowMoMs, int timeoutMs) {}

    /**
     * REST API settings.
     *
     * @param baseUrl   API URL; request paths such as "/customers" resolve against it
     * @param timeoutMs timeout of one API request in milliseconds
     */
    public record Api(String baseUrl, int timeoutMs) {}

    /**
     * One HTTP service (microservice).
     *
     * @param baseUrl   service URL; request paths such as "/orders" resolve against it
     * @param timeoutMs timeout of one request in milliseconds; null uses api.timeoutMs
     * @param headers   headers sent with every request to this service, for example an API key; may be null
     */
    public record Service(String baseUrl, Integer timeoutMs, Map<String, String> headers) {}

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
         * Settings used when the environment file has no healing section.
         *
         * @return enabled, 2 s probe, score 0.6, default file locations
         */
        public static Healing defaults() {
            return new Healing(true, 2000, 0.6, "healing/fingerprints.json", "target/healing/heals.jsonl");
        }
    }
}
