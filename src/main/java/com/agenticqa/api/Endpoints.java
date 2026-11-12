package com.agenticqa.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The endpoint catalog: every endpoint of every service, declared in YAML instead of Java, one file per service
 * under {@code src/main/resources/endpoints}. An endpoint is called by name, {@code FILE.KEY}, for example
 * {@code customers.create} is the key {@code create} in {@code endpoints/customers.yaml}:
 * <pre>
 * service: customers
 * endpoints:
 *   create: { method: POST, path: /customers, body: customers/new_customer.json }
 *   get:    { method: GET,  path: "/customers/{id}" }
 * </pre>
 * A file is read once per JVM and shared by all threads (read-only).
 */
public final class Endpoints {

    /** Classpath folder of the endpoint files. */
    public static final String FOLDER = "endpoints/";

    private static final Pattern PARAM = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Map<String, Group> FILES = new ConcurrentHashMap<>();

    /** Utility class, not instantiable. */
    private Endpoints() {}

    /** The content of one endpoint file. */
    private record Group(String service, Map<String, Spec> endpoints) {}

    /** One entry of an endpoint file, as written. */
    private record Spec(String method, String path, String body) {}

    /**
     * One endpoint.
     *
     * @param name    FILE.KEY, for example customers.create
     * @param service the service name under 'services:' in the environment file
     * @param method  HTTP verb
     * @param path    path template, for example /customers/{id}
     * @param body    default body file under src/test/resources/testdata, or null
     */
    public record Endpoint(String name, String service, String method, String path, String body) {

        /**
         * The path parameters of the template, in order.
         *
         * @return for example [id] for /customers/{id}
         */
        public Set<String> parameters() {
            Set<String> names = new LinkedHashSet<>();
            Matcher matcher = PARAM.matcher(path);
            while (matcher.find()) names.add(matcher.group(1));
            return names;
        }

        /**
         * Fills the path parameters, URL-encoding each value.
         *
         * @param values   explicit values by parameter name
         * @param fallback a value for a parameter without explicit value (for example a scenario variable of the
         *                 same name), or null
         * @return the path, for example /customers/42
         * @throws IllegalArgumentException if a parameter has no value
         */
        public String path(Map<String, ?> values, UnaryOperator<String> fallback) {
            Matcher matcher = PARAM.matcher(path);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String param = matcher.group(1);
                Object value = values.get(param);
                if (value == null && fallback != null) value = fallback.apply(param);
                if (value == null) {
                    throw new IllegalArgumentException("Endpoint '" + name + "' (" + method + " " + path
                            + ") needs path parameter '" + param + "'. Given: " + values.keySet());
                }
                String encoded = URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
                matcher.appendReplacement(out, Matcher.quoteReplacement(encoded));
            }
            matcher.appendTail(out);
            return out.toString();
        }
    }

    /**
     * Looks up an endpoint.
     *
     * @param name FILE.KEY, for example customers.create
     * @return the endpoint
     * @throws IllegalArgumentException if the file or the key does not exist (the message lists what does)
     */
    public static Endpoint get(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("Endpoint names are FILE.KEY, for example customers.create: " + name);
        }
        String file = name.substring(0, dot);
        String key = name.substring(dot + 1);
        Group group = FILES.computeIfAbsent(file, Endpoints::load);
        Spec spec = group.endpoints().get(key);
        if (spec == null) {
            throw new IllegalArgumentException("No endpoint '" + key + "' in " + FOLDER + file + ".yaml. Endpoints: "
                    + new TreeMap<>(group.endpoints()).keySet());
        }
        if (spec.method() == null || spec.path() == null) {
            throw new IllegalArgumentException(FOLDER + file + ".yaml: endpoint '" + key + "' needs method and path");
        }
        return new Endpoint(name, group.service(), spec.method().trim().toUpperCase(), spec.path().trim(), spec.body());
    }

    private static Group load(String file) {
        String resource = FOLDER + file.replace('.', '/') + ".yaml";
        try (InputStream in = Endpoints.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Endpoint file not found on the classpath: " + resource
                        + " (expected under src/main/resources/" + FOLDER + ")");
            }
            Group group = YAML.readValue(in, Group.class);
            if (group == null || group.service() == null || group.endpoints() == null) {
                throw new IllegalArgumentException(resource + " needs 'service:' and 'endpoints:'");
            }
            return group;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read endpoint file " + resource, e);
        }
    }
}
