package com.agenticqa.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locators of every page, read from JSON files instead of being written in page objects (a page object may use
 * several files, see {@link #find}):
 * {@code src/main/resources/locators/PAGE.json}, for example {@code locators/customers.json}.
 * <p>
 * Each entry maps a name to a selector, either short ({@code "searchInput": "#search-input"}) or as an object:
 * <pre>
 * "searchInput": { "selector": "#search-input", "description": "search input" },
 * "rows":        { "selector": "#customers-table tr.customer-row", "heal": false }
 * </pre>
 * {@code heal} (default true) makes the element a self-healing locator; {@code description} (default: the name)
 * is used in logs and heal reports. Keys starting with {@code _} are ignored, so a file can hold a
 * {@code "_comment"}. A file is read once per JVM and the result is shared by all threads (read-only).
 */
public final class LocatorRepository {

    /** Classpath folder of the locator files. */
    public static final String FOLDER = "locators/";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Map<String, Definition>> PAGES = new ConcurrentHashMap<>();

    /** Utility class, not instantiable. */
    private LocatorRepository() {}

    /**
     * One locator of a page.
     *
     * @param key         stable unique key, PAGE.NAME (for example customers.searchInput); it names the
     *                    element's fingerprint for self-healing
     * @param selector    the Playwright selector
     * @param description readable name for logs and reports
     * @param heal        true if the element should heal itself when the selector stops matching
     */
    public record Definition(String key, String selector, String description, boolean heal) {}

    /**
     * The locators of a page.
     *
     * @param page the page name, the file name without .json
     * @return name to definition, in file order (unmodifiable)
     * @throws IllegalArgumentException if the file is missing or an entry is malformed
     */
    public static Map<String, Definition> page(String page) {
        return PAGES.computeIfAbsent(page, LocatorRepository::load);
    }

    /**
     * One locator of a page.
     *
     * @param page the page name
     * @param name the locator name
     * @return the definition
     * @throws IllegalArgumentException if the page has no locator with that name (the message lists the names)
     */
    public static Definition get(String page, String name) {
        Definition definition = page(page).get(name);
        if (definition == null) {
            throw new IllegalArgumentException("No locator '" + name + "' in " + FOLDER + page + ".json. Locators: "
                    + page(page).keySet());
        }
        return definition;
    }

    /**
     * Finds a locator in several files, as a page object that uses more than one locator file does.
     * <ul>
     *   <li>{@code "searchInput"}: the files are searched in the order given; the first file that has the
     *       name wins, so a page's own file can override a shared one listed after it.</li>
     *   <li>{@code "pagination.nextLink"}: qualified with the file name, only that file is used (it need not
     *       be in the list). The part before the last dot is the file, so files in subfolders work too:
     *       {@code "customers/form.addButton"}.</li>
     * </ul>
     * Pure lookup, unit tested.
     *
     * @param files the locator files of the page, without .json, in search order
     * @param name  a locator name, plain or qualified as FILE.NAME
     * @return the definition
     * @throws IllegalArgumentException if no file has the name (the message lists every file's names)
     */
    public static Definition find(List<String> files, String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) return get(name.substring(0, dot), name.substring(dot + 1));
        for (String file : files) {
            Definition definition = page(file).get(name);
            if (definition != null) return definition;
        }
        StringBuilder known = new StringBuilder();
        for (String file : files) {
            known.append(System.lineSeparator()).append("  ").append(FOLDER).append(file).append(".json: ")
                    .append(page(file).keySet());
        }
        throw new IllegalArgumentException("No locator '" + name + "' in the locator files of this page." + known);
    }

    private static Map<String, Definition> load(String page) {
        String resource = FOLDER + page + ".json";
        JsonNode root;
        try (InputStream in = LocatorRepository.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Locator file not found on the classpath: " + resource
                        + " (expected under src/main/resources/" + FOLDER + ")");
            }
            root = MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read locator file " + resource, e);
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException(resource + " must hold a JSON object");

        Map<String, Definition> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = root.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String name = entry.getKey();
            if (name.startsWith("_")) continue;
            result.put(name, definition(resource, page + "." + name, name, entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Definition definition(String resource, String key, String name, JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) return new Definition(key, value.asText(), name, true);
        JsonNode selector = value.path("selector");
        if (!value.isObject() || !selector.isTextual() || selector.asText().isBlank()) {
            throw new IllegalArgumentException(resource + ": locator '" + name + "' needs a selector, either "
                    + "\"" + name + "\": \"#id\" or \"" + name + "\": { \"selector\": \"#id\" }");
        }
        String description = value.path("description").asText(name);
        return new Definition(key, selector.asText(), description, value.path("heal").asBoolean(true));
    }
}
