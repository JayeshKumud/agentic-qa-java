package com.agenticqa.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads config/common.properties with config/env/NAME.properties on top (a key in the environment file wins) and
 * fills in ${NAME} / ${NAME:-fallback} placeholders. Values are read through {@link Settings}.
 */
public final class EnvConfig {

    /** Environments shipped with the framework, listed when an unknown name is used. */
    public static final String KNOWN = "dev, test, uat, ci";

    /** Classpath resource with the settings shared by every environment. */
    public static final String COMMON = "/config/common.properties";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(?::-([^}]*))?}");

    private static volatile Properties properties;
    private static volatile String defaultEnv;

    private EnvConfig() {}

    /** Active environment: -Denv, else the ENV variable, else environment in common.properties, else dev. */
    public static String envName() {
        String env = System.getProperty("env");
        if (env == null || env.isBlank()) env = System.getenv("ENV");
        if (env == null || env.isBlank()) env = defaultEnv();
        return (env == null || env.isBlank()) ? "dev" : env.trim();
    }

    /** The merged properties of the active environment, loaded once per JVM. */
    public static Properties properties() {
        if (properties == null) {
            synchronized (EnvConfig.class) {
                if (properties == null) properties = load();
            }
        }
        return properties;
    }

    /** Replaces every ${NAME} and ${NAME:-fallback} in a text; fails if a placeholder without fallback has no value. */
    public static String fill(String text, UnaryOperator<String> lookup) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = lookup.apply(name);
            if (value == null) value = matcher.group(2);
            if (value == null) {
                throw new IllegalStateException("config/common.properties or config/env/" + envName()
                        + ".properties uses ${" + name + "}, but it is not set. Set the environment variable "
                        + name + ", pass -D" + name + "=..., or write ${" + name + ":-default} in the file.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String defaultEnv() {
        if (defaultEnv == null) {
            String value = read(COMMON, "Shared settings file not found on classpath: " + COMMON).getProperty("environment");
            defaultEnv = value == null ? "" : value.trim();
        }
        return defaultEnv;
    }

    private static Properties load() {
        String resource = "/config/env/" + envName() + ".properties";
        Properties merged = read(COMMON, "Shared settings file not found on classpath: " + COMMON);
        merged.putAll(read(resource, "Environment file not found on classpath: " + resource
                + ". Known environments: " + KNOWN + " (src/main/resources/config/env)."));
        UnaryOperator<String> lookup = name -> System.getProperty(name, System.getenv(name));
        for (String key : merged.stringPropertyNames()) {
            merged.setProperty(key, fill(merged.getProperty(key), lookup).trim());
        }
        merged.setProperty("environment", envName()); // the environment that actually runs, also with -Denv
        return merged;
    }

    private static Properties read(String resource, String missing) {
        try (InputStream in = EnvConfig.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException(missing);
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
