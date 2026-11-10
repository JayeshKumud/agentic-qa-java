package com.agenticqa.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads and changes values deep inside a JSON document by a simple path, so large payloads can be edited field
 * by field from feature files. Pure logic without a browser, unit tested in JsonPathsTest.
 * <p>
 * Path syntax (a leading {@code $} or {@code $.} is allowed and ignored):
 * <ul>
 *   <li>{@code name} or {@code profile.address.city}: object fields</li>
 *   <li>{@code items[0].qty}: array index; {@code items[-1]} is the last element</li>
 *   <li>{@code tags[]}: append (only when setting)</li>
 *   <li>{@code ["field.with.dots"]}: a field name that contains dots or brackets</li>
 *   <li>{@code $} or an empty path: the whole document</li>
 * </ul>
 * Setting a value creates missing objects and arrays on the way, so a request can be built from nothing.
 */
public final class JsonPaths {

    /** Prefix that marks a feature-file value as JSON instead of text, for example {@code json:42}. */
    public static final String JSON_PREFIX = "json:";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Utility class, not instantiable. */
    private JsonPaths() {}

    /** One step of a path. */
    private sealed interface Segment permits Field, Index, Append {}

    private record Field(String name) implements Segment {
        @Override public String toString() { return name; }
    }

    private record Index(int index) implements Segment {
        @Override public String toString() { return "[" + index + "]"; }
    }

    private record Append() implements Segment {
        @Override public String toString() { return "[]"; }
    }

    /**
     * Parses JSON text.
     *
     * @param text the JSON
     * @return the tree
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static JsonNode parse(String text) {
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node == null) throw new IllegalArgumentException("Empty JSON");
            return node;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getOriginalMessage() + System.lineSeparator()
                    + text, e);
        }
    }

    /**
     * Writes a tree as compact JSON text.
     *
     * @param node the tree
     * @return its JSON text
     */
    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Turns a feature-file value into a JSON value: {@code json:...} is parsed as JSON (number, boolean, null,
     * object, array or quoted string); anything else is a plain string.
     *
     * @param raw the value as written
     * @return the JSON value
     * @throws IllegalArgumentException if a {@code json:} value is not valid JSON
     */
    public static JsonNode value(String raw) {
        if (raw != null && raw.startsWith(JSON_PREFIX)) return parse(raw.substring(JSON_PREFIX.length()).trim());
        return TextNode.valueOf(raw == null ? "" : raw);
    }

    /**
     * Reads the value at a path.
     *
     * @param root the document
     * @param path the path
     * @return the value, or null if the path leads nowhere
     */
    public static JsonNode get(JsonNode root, String path) {
        JsonNode node = root;
        for (Segment segment : segments(path)) {
            node = child(node, segment);
            if (node == null) return null;
        }
        return node;
    }

    /**
     * Sets the value at a path, creating missing objects and arrays on the way.
     *
     * @param root  the document (an object or an array)
     * @param path  the path, not empty
     * @param value the new value
     * @throws IllegalArgumentException if the path does not fit the document, for example an index past the end
     */
    public static void set(JsonNode root, String path, JsonNode value) {
        List<Segment> segments = segments(path);
        if (segments.isEmpty()) throw new IllegalArgumentException("Cannot set the whole document with path '" + path + "'");
        JsonNode parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            Segment segment = segments.get(i);
            JsonNode next = child(parent, segment);
            if (next == null || next.isNull()) {
                next = segments.get(i + 1) instanceof Field ? MAPPER.createObjectNode() : MAPPER.createArrayNode();
                put(parent, segment, next, path);
            } else if (!next.isContainerNode()) {
                throw new IllegalArgumentException("Path '" + path + "': '" + prefix(segments, i + 1)
                        + "' is " + next.getNodeType().name().toLowerCase() + ", not an object or array");
            }
            parent = next;
        }
        put(parent, segments.get(segments.size() - 1), value, path);
    }

    /**
     * Removes the value at a path (an object field or an array element).
     *
     * @param root the document
     * @param path the path, not empty
     * @throws IllegalArgumentException if there is nothing at that path, which is almost always a typo
     */
    public static void remove(JsonNode root, String path) {
        List<Segment> segments = segments(path);
        if (segments.isEmpty()) throw new IllegalArgumentException("Cannot remove the whole document");
        JsonNode parent = root;
        for (int i = 0; i < segments.size() - 1 && parent != null; i++) parent = child(parent, segments.get(i));
        Segment last = segments.get(segments.size() - 1);
        if (last instanceof Field f && parent instanceof ObjectNode object && object.has(f.name())) {
            object.remove(f.name());
        } else if (last instanceof Index idx && parent instanceof ArrayNode array && inRange(array, idx.index())) {
            array.remove(resolve(array, idx.index()));
        } else {
            throw new IllegalArgumentException("Nothing to remove at '" + path + "'");
        }
    }

    /**
     * Deep-merges a patch into a document (JSON Merge Patch, RFC 7386): objects are merged field by field,
     * a {@code null} in the patch removes the field, any other value (including arrays) replaces it.
     *
     * @param target the document to change, an object
     * @param patch  the changes, an object
     * @throws IllegalArgumentException if either is not an object
     */
    public static void merge(JsonNode target, JsonNode patch) {
        if (!(target instanceof ObjectNode t) || !(patch instanceof ObjectNode p)) {
            throw new IllegalArgumentException("Only JSON objects can be merged");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = p.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            JsonNode existing = t.get(field.getKey());
            if (value.isNull()) {
                t.remove(field.getKey());
            } else if (value.isObject() && existing != null && existing.isObject()) {
                merge(existing, value);
            } else {
                t.set(field.getKey(), value.deepCopy());
            }
        }
    }

    /**
     * Compares a value from a document with a feature-file expectation. A {@code json:} expectation is compared
     * as JSON; a plain one is compared with the value's text, so {@code 42}, {@code true} and {@code active}
     * work without the prefix.
     *
     * @param actual   the value found, may be null
     * @param expected the expectation as written
     * @return true if they match
     */
    public static boolean matches(JsonNode actual, String expected) {
        if (actual == null) return false;
        if (expected.startsWith(JSON_PREFIX)) return actual.equals(value(expected));
        return actual.isValueNode() && !actual.isNull() && actual.asText().equals(expected);
    }

    // ------------------------------------------------------------------------------------------------ internals

    private static JsonNode child(JsonNode node, Segment segment) {
        if (segment instanceof Field f) return node instanceof ObjectNode ? node.get(f.name()) : null;
        if (segment instanceof Index i) {
            return node instanceof ArrayNode a && inRange(a, i.index()) ? a.get(resolve(a, i.index())) : null;
        }
        return null; // append has no current value
    }

    private static void put(JsonNode parent, Segment segment, JsonNode value, String path) {
        if (segment instanceof Field f) {
            if (!(parent instanceof ObjectNode object)) {
                throw new IllegalArgumentException("Path '" + path + "': field '" + f.name() + "' needs an object, found "
                        + parent.getNodeType().name().toLowerCase());
            }
            object.set(f.name(), value);
            return;
        }
        if (!(parent instanceof ArrayNode array)) {
            throw new IllegalArgumentException("Path '" + path + "': '" + segment + "' needs an array, found "
                    + parent.getNodeType().name().toLowerCase());
        }
        if (segment instanceof Append) {
            array.add(value);
        } else {
            int index = ((Index) segment).index();
            if (inRange(array, index)) array.set(resolve(array, index), value);
            else if (index == array.size()) array.add(value);
            else throw new IllegalArgumentException("Path '" + path + "': index " + index + " is out of range (size "
                        + array.size() + "); use [] to append");
        }
    }

    private static boolean inRange(ArrayNode array, int index) {
        int i = resolve(array, index);
        return i >= 0 && i < array.size();
    }

    private static int resolve(ArrayNode array, int index) {
        return index < 0 ? array.size() + index : index;
    }

    private static String prefix(List<Segment> segments, int count) {
        return join(segments.subList(0, count));
    }

    private static String join(List<Segment> segments) {
        StringBuilder out = new StringBuilder();
        for (Segment s : segments) {
            if (s instanceof Field f) {
                boolean plain = f.name().matches("[^.\\[\\]\"']+");
                if (plain) out.append(out.isEmpty() ? "" : ".").append(f.name());
                else out.append("[\"").append(f.name()).append("\"]");
            } else {
                out.append(s);
            }
        }
        return out.toString();
    }

    /**
     * Splits a path into segments.
     *
     * @param path the path
     * @return its segments, empty for the whole document
     * @throws IllegalArgumentException if the path is malformed
     */
    private static List<Segment> segments(String path) {
        String p = path == null ? "" : path.trim();
        if (p.equals("$")) p = "";
        else if (p.startsWith("$.")) p = p.substring(2);
        else if (p.startsWith("$[")) p = p.substring(1);

        List<Segment> result = new ArrayList<>();
        int i = 0;
        while (i < p.length()) {
            char c = p.charAt(i);
            if (c == '.') {
                if (i == 0 || i == p.length() - 1 || p.charAt(i + 1) == '.') throw malformed(path);
                i++;
            } else if (c == '[') {
                int end = p.indexOf(']', i);
                if (end < 0) throw malformed(path);
                String inside = p.substring(i + 1, end).trim();
                if (inside.isEmpty()) {
                    result.add(new Append());
                } else if (inside.length() >= 2 && (inside.charAt(0) == '"' || inside.charAt(0) == '\'')
                        && inside.charAt(inside.length() - 1) == inside.charAt(0)) {
                    result.add(new Field(inside.substring(1, inside.length() - 1)));
                } else {
                    try {
                        result.add(new Index(Integer.parseInt(inside)));
                    } catch (NumberFormatException e) {
                        throw malformed(path);
                    }
                }
                i = end + 1;
            } else {
                int end = i;
                while (end < p.length() && p.charAt(end) != '.' && p.charAt(end) != '[') end++;
                result.add(new Field(p.substring(i, end)));
                i = end;
            }
        }
        return result;
    }

    private static IllegalArgumentException malformed(String path) {
        return new IllegalArgumentException("Malformed JSON path '" + path
                + "'. Use for example: name, profile.address.city, items[0].qty, tags[] or [\"odd.key\"]");
    }
}
