package com.agenticqa.config;

/**
 * Typed view of one environment file (config/env/NAME.yaml).
 * Kept minimal on purpose: new sections (healing, reporting, ...) are added in the step that needs them.
 *
 * @param ui      browser and GUI settings
 * @param api     REST API settings
 * @param healing self-healing locator settings; defaults apply when the section is missing
 */
public record Settings(Ui ui, Api api, Healing healing) {

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
