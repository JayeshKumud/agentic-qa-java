package com.agenticqa.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Named values of one scenario, used to chain requests: a step saves a value from a response (for example a new
 * order id) and later steps use it as {@code ${orderId}} in URLs, request values, headers, files and
 * expectations. PicoContainer creates one instance per scenario and shares it with every step class.
 * <p>
 * {@code {unique}} is also replaced: by one random token per scenario, so test data never collides between
 * parallel scenarios.
 */
public class Variables {

    /** Placeholder replaced by the scenario's random token. */
    public static final String UNIQUE = "{unique}";

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private final Map<String, String> values = new LinkedHashMap<>();
    private String token;

    /**
     * Stores a value.
     *
     * @param name  the variable name (letters, digits, _ . -)
     * @param value the value
     * @throws IllegalArgumentException if the name has other characters
     */
    public synchronized void put(String name, String value) {
        if (!name.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Variable names may only contain letters, digits, _ . and -: " + name);
        }
        values.put(name, value);
    }

    /**
     * Reads a value.
     *
     * @param name the variable name
     * @return the value
     * @throws IllegalStateException if nothing was saved under that name
     */
    public synchronized String get(String name) {
        String value = values.get(name);
        if (value == null) {
            throw new IllegalStateException("Unknown variable ${" + name + "}. Saved so far: " + values.keySet()
                    + ". Save one first, for example: I save the \"x\" response field \"id\" as \"" + name + "\"");
        }
        return value;
    }

    /**
     * Reads a value if it exists.
     *
     * @param name the variable name
     * @return the value, or null if nothing was saved under that name
     */
    public synchronized String find(String name) {
        return values.get(name);
    }

    /**
     * The scenario's token, created on first use.
     *
     * @return 8 hex characters, the same for the whole scenario
     */
    public synchronized String token() {
        if (token == null) token = TestData.token();
        return token;
    }

    /**
     * Replaces {@code {unique}} and every {@code ${name}} in a text.
     *
     * @param text the text, may be null
     * @return the text with all placeholders filled in
     * @throws IllegalStateException if a referenced variable does not exist
     */
    public synchronized String resolve(String text) {
        if (text == null) return null;
        String result = text.contains(UNIQUE) ? text.replace(UNIQUE, token()) : text;
        Matcher matcher = REFERENCE.matcher(result);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(out, Matcher.quoteReplacement(get(matcher.group(1))));
        matcher.appendTail(out);
        return out.toString();
    }
}
