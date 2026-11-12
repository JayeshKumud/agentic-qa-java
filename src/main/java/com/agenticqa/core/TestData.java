package com.agenticqa.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates test data that is unique per call, so scenarios running in parallel against the same app never
 * see each other's records, and a search for one scenario's data finds only that data.
 * It also reads test data files from the {@value #FOLDER} folder of the classpath
 * (src/test/resources/testdata).
 */
public final class TestData {

    /** Classpath folder of the test data files. */
    public static final String FOLDER = "testdata/";

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
     * A number that no other call returns in this run, even from parallel threads, and that is larger than any
     * number of earlier runs on this machine (it starts at the current time in milliseconds), for ids such as a
     * zone id.
     *
     * @return for example 1762950712345
     */
    public static long uniqueNumber() {
        return NUMBERS.updateAndGet(last -> Math.max(last + 1, System.currentTimeMillis()));
    }

    private static final AtomicLong NUMBERS = new AtomicLong();

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

    /**
     * Reads a test data file as text, for example a JSON request body.
     *
     * @param path path inside src/test/resources/testdata, for example "customers/new_customer.json"
     * @return the file content
     * @throws IllegalArgumentException if the file does not exist
     */
    public static String file(String path) {
        String resource = FOLDER + path.replace('\\', '/').replaceFirst("^/+", "");
        try (InputStream in = TestData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Test data file not found on the classpath: " + resource
                        + " (expected under src/test/resources/" + FOLDER + ")");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read test data file " + resource, e);
        }
    }
}
