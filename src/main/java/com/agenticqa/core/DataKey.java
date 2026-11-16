package com.agenticqa.core;

import java.util.List;

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

    /**
     * Creates a key for a list, which {@link #of} cannot type because {@code List.class} has no element type:
     * <pre>
     * public static final DataKey&lt;List&lt;String&gt;&gt; VIEWS = DataKey.listOf("views", String.class);
     * </pre>
     * The cast is safe: values reach the store only through {@code TestSession.put(DataKey<T>, T)}, so the compiler
     * already checks that a {@code List<E>} is stored under the key.
     *
     * @param name        a short readable name
     * @param elementType the type of the list elements
     * @param <E>         the type of the list elements
     * @return the key
     */
    @SuppressWarnings("unchecked")
    public static <E> DataKey<List<E>> listOf(String name, Class<E> elementType) {
        return new DataKey<>(name, (Class<List<E>>) (Class<?>) List.class);
    }
}
