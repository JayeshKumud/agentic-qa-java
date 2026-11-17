package com.agenticqa.config;

/**
 * Static lookups for API classes in the style of the existing framework ({@code com.agenticqa.api.Api}), which
 * read their settings in a no-argument constructor:
 * <pre>
 * this.baseUrl = Config.getApiUrl("customers");
 * </pre>
 * The values come from the same place as everything else: config/common.yaml merged with the active
 * environment file ({@code -Denv=...}) and any {@code -D} overrides, see {@link EnvConfig}.
 */
public final class Config {

    private Config() {}

    /**
     * Base URL of a service of the active environment, {@code services.<apiName>.baseUrl} in the environment file.
     *
     * @param apiName the service name, for example "customers"
     * @return the base URL, for example http://127.0.0.1:8000
     * @throws IllegalArgumentException if the service is not configured
     * @throws IllegalStateException    if the service has no baseUrl
     */
    public static String getApiUrl(String apiName) {
        String baseUrl = EnvConfig.get().service(apiName).baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("services." + apiName + ".baseUrl is not set in config/env/"
                    + EnvConfig.envName() + ".yaml");
        }
        return baseUrl;
    }

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
