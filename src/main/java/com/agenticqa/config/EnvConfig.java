package com.agenticqa.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the settings of the active environment from config/env/NAME.yaml (on the classpath).
 * Choose the environment with -Denv=test or the ENV variable; the default is dev.
 * <p>
 * Values may contain placeholders, so URLs and secrets of shared environments (test, uat) need not be committed:
 * {@code ${NAME}} is replaced by the system property NAME (-DNAME=...) or else the environment variable NAME, and
 * {@code ${NAME:-fallback}} uses the fallback when neither is set. A placeholder without fallback whose value is
 * missing fails at startup, naming the variable. Placeholders in YAML comments are ignored.
 */
public final class EnvConfig {

    /** Environments shipped with the framework, listed when an unknown name is used. */
    public static final String KNOWN = "dev, test, uat, ci";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(?::-([^}]*))?}");

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
     * @throws IllegalStateException if config/env/NAME.yaml does not exist or a placeholder has no value
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
     * Replaces every {@code ${NAME}} and {@code ${NAME:-fallback}} in a text. Pure logic, unit tested.
     *
     * @param text   the text
     * @param lookup gives the value of a name, or null if it is not set
     * @return the text with all placeholders filled in
     * @throws IllegalStateException if a placeholder without fallback has no value
     */
    public static String fill(String text, UnaryOperator<String> lookup) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = lookup.apply(name);
            if (value == null) value = matcher.group(2);
            if (value == null) {
                throw new IllegalStateException("config/env/" + envName() + ".yaml uses ${" + name + "}, but it is "
                        + "not set. Set the environment variable " + name + ", pass -D" + name + "=..., or write "
                        + "${" + name + ":-default} in the file.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Reads the YAML file of the active environment, fills in placeholders and binds it.
     *
     * @return the typed settings
     * @throws IllegalStateException if the file does not exist
     * @throws UncheckedIOException  if the file cannot be read or parsed
     */
    private static Settings load() {
        String resource = "/config/env/" + envName() + ".yaml";
        try (InputStream in = EnvConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Environment file not found on classpath: " + resource
                        + ". Known environments: " + KNOWN + " (src/main/resources/config/env).");
            }
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JsonNode tree = mapper.readTree(in);
            fillTree(tree);
            return mapper.treeToValue(tree, Settings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    /**
     * Fills placeholders in every text value of the parsed file (after parsing, so comments are never touched).
     *
     * @param node the parsed YAML
     */
    private static void fillTree(JsonNode node) {
        UnaryOperator<String> lookup = name -> {
            String value = System.getProperty(name);
            return value != null ? value : System.getenv(name);
        };
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isTextual()) {
                    field.setValue(TextNode.valueOf(fill(field.getValue().asText(), lookup)));
                } else {
                    fillTree(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i).isTextual()) array.set(i, TextNode.valueOf(fill(array.get(i).asText(), lookup)));
                else fillTree(array.get(i));
            }
        }
    }
}
