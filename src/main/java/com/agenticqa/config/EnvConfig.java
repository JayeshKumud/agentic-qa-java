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
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the settings of the active environment. Two files on the classpath (src/main/resources) are read and
 * merged: {@value #COMMON} holds what every environment shares (browser, waits, endpoint catalog, execution
 * mode, cleanup, healing, retry), and config/env/NAME.yaml holds what differs per environment (URLs, client
 * ids and secrets, overrides). A key set in the environment file wins; nested sections are merged key by key,
 * lists are replaced. Choose the environment with -Denv=test or the ENV variable; the default is dev.
 * <p>
 * Any value can be overridden for one run with a system property named by its path, for example
 * {@code -Dui.headless=false}, {@code -Dui.waits.actionMs=30000}, {@code -Dservices.customers.baseUrl=http://...},
 * {@code -Dretry.count=2} or {@code -Dexecution.defaultMode=sequential}.
 * <p>
 * Values may contain placeholders, so URLs and secrets of shared environments (test, uat) need not be committed:
 * {@code ${NAME}} is replaced by the system property NAME (-DNAME=...) or else the environment variable NAME, and
 * {@code ${NAME:-fallback}} uses the fallback when neither is set. A placeholder without fallback whose value is
 * missing fails at startup, naming the variable. Placeholders in YAML comments are ignored.
 */
public final class EnvConfig {

    /** Environments shipped with the framework, listed when an unknown name is used. */
    public static final String KNOWN = "dev, test, uat, ci";

    /** Classpath resource with the settings shared by every environment. */
    public static final String COMMON = "/config/common.yaml";

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
                throw new IllegalStateException("config/common.yaml or config/env/" + envName() + ".yaml uses ${"
                        + name + "}, but it is "
                        + "not set. Set the environment variable " + name + ", pass -D" + name + "=..., or write "
                        + "${" + name + ":-default} in the file.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Deep-merges one parsed YAML tree into another: objects are merged key by key (recursively), any other
     * value, lists included, replaces the old one. Pure logic, unit tested.
     *
     * @param base     the tree to change, for example common.yaml
     * @param override the tree whose values win, for example the environment file
     * @return the merged tree (base, changed, when both are objects)
     */
    public static JsonNode merge(JsonNode base, JsonNode override) {
        if (override == null || override.isMissingNode() || override.isNull()) return base;
        if (!(base instanceof ObjectNode target) || !(override instanceof ObjectNode source)) return override;
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode current = target.get(field.getKey());
            target.set(field.getKey(), current == null ? field.getValue() : merge(current, field.getValue()));
        }
        return target;
    }

    /**
     * Applies -D overrides: every system property whose name is a path into one of the tree's top-level
     * sections ({@code ui.headless}, {@code services.customers.baseUrl}, ...) replaces that value; missing
     * sections on the way are created. Properties of other names (env, threads, java.version, ...) are ignored.
     * Pure logic, unit tested.
     *
     * @param tree       the merged settings
     * @param properties the system properties
     */
    public static void applyOverrides(ObjectNode tree, Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            String[] path = name.split("\\.");
            if (path.length < 2 || !(tree.get(path[0]) instanceof ObjectNode)) continue;
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) continue;
            ObjectNode node = tree;
            for (int i = 0; i < path.length - 1; i++) {
                JsonNode child = node.get(path[i]);
                node = child instanceof ObjectNode object ? object : node.putObject(path[i]);
            }
            node.put(path[path.length - 1], value.trim());
        }
    }

    /**
     * Reads common.yaml and the environment file, merges them, fills in placeholders, applies -D overrides and
     * binds the result.
     *
     * @return the typed settings
     * @throws IllegalStateException if a file does not exist or a placeholder has no value
     * @throws UncheckedIOException  if a file cannot be read or parsed
     */
    private static Settings load() {
        String resource = "/config/env/" + envName() + ".yaml";
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode common = read(mapper, COMMON, "Shared settings file not found on classpath: " + COMMON
                + " (src/main/resources/config/common.yaml).");
        JsonNode env = read(mapper, resource, "Environment file not found on classpath: " + resource
                + ". Known environments: " + KNOWN + " (src/main/resources/config/env).");
        JsonNode tree = merge(common, env);
        fillTree(tree);
        applyOverrides((ObjectNode) tree, System.getProperties());
        try {
            return mapper.treeToValue(tree, Settings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot bind " + COMMON + " + " + resource, e);
        }
    }

    /**
     * Parses one YAML resource.
     *
     * @param mapper   the YAML mapper
     * @param resource the classpath resource
     * @param missing  the error message if it does not exist
     * @return the parsed tree; an empty object for an empty file
     */
    private static ObjectNode read(ObjectMapper mapper, String resource, String missing) {
        try (InputStream in = EnvConfig.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException(missing);
            JsonNode tree = mapper.readTree(in);
            if (tree == null || tree.isMissingNode() || tree.isNull()) return mapper.createObjectNode();
            if (!(tree instanceof ObjectNode object)) {
                throw new IllegalStateException(resource + " must be a YAML mapping (key: value), not " + tree.getNodeType());
            }
            return object;
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
