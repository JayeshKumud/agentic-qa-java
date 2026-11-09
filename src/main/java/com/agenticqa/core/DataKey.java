package com.agenticqa.core;

/**
 * A typed key for data shared between the steps of one scenario.
 * Declare keys once as constants, so the compiler checks the type and no cast is needed when reading.
 * <pre>
 * public static final DataKey&lt;String&gt; TOKEN = DataKey.of("token", String.class);
 * </pre>
 *
 * @param name a short readable name, shown in error messages
 * @param type the type of the stored value
 * @param <T>  the type of the stored value
 */
public record DataKey<T>(String name, Class<T> type) {

    /**
     * Creates a key.
     *
     * @param name a short readable name
     * @param type the type of the stored value
     * @param <T>  the type of the stored value
     * @return the key
     */
    public static <T> DataKey<T> of(String name, Class<T> type) {
        return new DataKey<>(name, type);
    }
}
