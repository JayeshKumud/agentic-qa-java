package com.agenticqa.config;

/**
 * Run-level switches that come from system properties and environment variables, not from the YAML files.
 * Settings from the YAML files (service base URLs, jks, ui, ...) are read with {@code EnvConfig.get()}, see
 * {@link Settings}.
 */
public final class Config {

    private Config() {}

    /**
     * Tells whether only the scenarios enabled by feature flags run ({@code FeatureAwareManager} in TestRunner).
     *
     * @return true with {@code -DfeatureAware=true} or the environment variable FEATURE_AWARE=true
     */
    public static boolean isFeatureAware() {
        return Boolean.parseBoolean(System.getProperty("featureAware", System.getenv("FEATURE_AWARE")));
    }

    /**
     * Tells whether the run is in the pipeline: the CI environment variable is set (GitHub Actions, GitLab and the
     * Jenkins plugins set it), or the environment is ci ({@code -Denv=ci}).
     *
     * @return true in the pipeline
     */
    public static boolean isRunningInPipeline() {
        String ci = System.getenv("CI");
        return (ci != null && !ci.isBlank() && !ci.equalsIgnoreCase("false")) || "ci".equals(EnvConfig.envName());
    }
}
