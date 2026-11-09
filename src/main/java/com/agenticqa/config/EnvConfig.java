package com.agenticqa.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads the settings of the active environment from config/env/NAME.yaml (on the classpath).
 * Choose the environment with -Denv=test or the ENV variable; the default is dev.
 */
public final class EnvConfig {

    private static volatile Settings settings;

    /** Utility class, not instantiable. */
    private EnvConfig() {}

    /**
     * Name of the active environment.
     *
     * @return the -Denv system property, else the ENV environment variable, else "dev"
     */
    public static String envName() {
        String env = System.getProperty("env");
        if (env == null || env.isBlank()) env = System.getenv("ENV");
        return (env == null || env.isBlank()) ? "dev" : env.trim();
    }

    /**
     * Settings of the active environment. The file is read once per JVM; the call is thread-safe.
     *
     * @return the typed settings
     * @throws IllegalStateException if config/env/NAME.yaml does not exist
     */
    public static Settings get() {
        if (settings == null) {
            synchronized (EnvConfig.class) {
                if (settings == null) settings = load();
            }
        }
        return settings;
    }

    /**
     * Reads and binds the YAML file of the active environment.
     *
     * @return the typed settings
     * @throws IllegalStateException if the file does not exist
     * @throws UncheckedIOException  if the file cannot be read or parsed
     */
    private static Settings load() {
        String resource = "/config/env/" + envName() + ".yaml";
        try (InputStream in = EnvConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Environment file not found on classpath: " + resource);
            }
            return new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, Settings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
