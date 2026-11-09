package com.agenticqa.ui.healing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

/**
 * The baseline of element fingerprints, one JSON file shared by all scenarios of the JVM. It is read once and
 * rewritten only when a fingerprint changes. All methods are synchronized, because scenarios run in parallel.
 */
public final class FingerprintStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private static FingerprintStore instance;

    private final Path file;
    private final Map<String, Fingerprint> fingerprints;

    /**
     * Loads the file, or starts empty when it does not exist yet.
     *
     * @param file the JSON file
     */
    private FingerprintStore(Path file) {
        this.file = file;
        this.fingerprints = load(file);
    }

    /**
     * The store of the JVM. The first call decides the file.
     *
     * @param file path of the fingerprints file
     * @return the shared store
     */
    public static synchronized FingerprintStore at(String file) {
        if (instance == null) instance = new FingerprintStore(Paths.get(file));
        return instance;
    }

    /**
     * The last known fingerprint of an element.
     *
     * @param key the element key, for example "customers.searchInput"
     * @return the fingerprint, or null if the element never worked yet
     */
    public synchronized Fingerprint get(String key) {
        return fingerprints.get(key);
    }

    /**
     * Records the fingerprint of an element whose selector worked; writes the file only if it changed.
     *
     * @param key         the element key
     * @param fingerprint what the element looks like now
     */
    public synchronized void put(String key, Fingerprint fingerprint) {
        if (fingerprint.equals(fingerprints.get(key))) return;
        fingerprints.put(key, fingerprint);
        save();
    }

    /** Writes all fingerprints, sorted by key so the file diffs cleanly in version control. */
    private void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), fingerprints);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /**
     * Reads the fingerprints file.
     *
     * @param file the JSON file
     * @return its content, or an empty map if the file does not exist
     */
    private static Map<String, Fingerprint> load(Path file) {
        if (!Files.exists(file)) return new TreeMap<>();
        try {
            return new TreeMap<>(MAPPER.readValue(file.toFile(), new TypeReference<Map<String, Fingerprint>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }
}
