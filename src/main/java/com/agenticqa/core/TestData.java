package com.agenticqa.core;

import java.util.UUID;

/**
 * Generates test data that is unique per call, so scenarios running in parallel against the same app never
 * see each other's records, and a search for one scenario's data finds only that data.
 */
public final class TestData {

    /** Utility class, not instantiable. */
    private TestData() {}

    /**
     * A short random token.
     *
     * @return 8 lowercase hex characters, for example 3f9a1c2b
     */
    public static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Makes a readable name unique.
     *
     * @param base the readable part, for example "Ada Lovelace"
     * @return for example "Ada Lovelace 3f9a1c2b"
     */
    public static String uniqueName(String base) {
        return base + " " + token();
    }

    /**
     * Derives an email from a (unique) name.
     *
     * @param name the name, for example "Ada Lovelace 3f9a1c2b"
     * @return for example ada-lovelace-3f9a1c2b@example.test
     */
    public static String emailFor(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug + "@example.test";
    }
}