package com.agenticqa.api;

import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings.EndpointGroup;
import com.agenticqa.config.Settings.EndpointSpec;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The endpoint catalog: every endpoint of every service, declared once in YAML instead of Java, under
 * {@code endpoints:} in {@code src/main/resources/config/common.yaml}. Paths are the same in every environment;
 * only the service's base URL (and auth) changes, and that comes from the environment file. An endpoint is called
 * by name, {@code GROUP.KEY}, for example {@code customers.create}:
 * <pre>
 * endpoints:
 *   customers:
 *     service: customers
 *     endpoints:
 *       create: { method: POST, path: /customers, body: customers/new_customer.json }
 *       get:    { method: GET,  path: "/customers/{id}" }
 * </pre>
 * The catalog is part of the settings, read once per JVM and shared by all threads (read-only). An environment
 * file may override an entry, though that is rarely needed.
 */
public final class Endpoints {

    /** Where the catalog lives, for error messages. */
    public static final String SOURCE = "config/common.yaml (endpoints:)";

    private static final Pattern PARAM = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /** Utility class, not instantiable. */
    private Endpoints() {}

    /**
     * One endpoint.
     *
     * @param name    GROUP.KEY, for example customers.create
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
     * @param name GROUP.KEY, for example customers.create
     * @return the endpoint
     * @throws IllegalArgumentException if the group or the key does not exist (the message lists what does)
     */
    public static Endpoint get(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("Endpoint names are GROUP.KEY, for example customers.create: " + name);
        }
        String groupName = name.substring(0, dot);
        String key = name.substring(dot + 1);
        Map<String, EndpointGroup> catalog = EnvConfig.get().endpoints();
        EndpointGroup group = catalog.get(groupName);
        if (group == null) {
            throw new IllegalArgumentException("No endpoint group '" + groupName + "' in " + SOURCE + ". Groups: "
                    + new TreeMap<>(catalog).keySet());
        }
        if (group.service() == null || group.endpoints() == null) {
            throw new IllegalArgumentException(SOURCE + ": group '" + groupName + "' needs 'service:' and 'endpoints:'");
        }
        EndpointSpec spec = group.endpoints().get(key);
        if (spec == null) {
            throw new IllegalArgumentException("No endpoint '" + key + "' in group '" + groupName + "' of " + SOURCE
                    + ". Endpoints: " + new TreeMap<>(group.endpoints()).keySet());
        }
        if (spec.method() == null || spec.path() == null) {
            throw new IllegalArgumentException(SOURCE + ": endpoint '" + name + "' needs method and path");
        }
        return new Endpoint(name, group.service(), spec.method().trim().toUpperCase(), spec.path().trim(), spec.body());
    }
}
