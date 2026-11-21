package com.agenticqa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The data one scenario shares between its steps, GUI and API steps alike (an API step stores the customer it
 * created, a GUI step reads it), under typed keys ({@link DataKey}):
 * <pre>
 * session.data().put(Keys.CUSTOMER, customer);
 * Customer customer = session.data().get(Keys.CUSTOMER);
 * session.data().add(Keys.CREATED_INST_MAN, instMan);         // list key
 * session.data().put(Keys.BASE_UNIQUE, "U1", unique);          // map key
 * </pre>
 * Each {@link TestSession} owns one, so it lives and ends with the scenario and parallel scenarios never share
 * data. Code that PicoContainer does not create reaches it with {@code TestSession.current().data()}.
 */
public class TestData {

    private final Map<DataKey<?>, Object> store = new HashMap<>();

    /**
     * Stores a value for later steps of the same scenario.
     *
     * @param key   the typed key
     * @param value the value to keep
     * @param <T>   the type of the value
     */
    public synchronized <T> void put(DataKey<T> key, T value) {
        store.put(key, value);
    }

    /**
     * Reads a stored value.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return the stored value
     * @throws IllegalStateException if nothing was stored under the key in this scenario
     */
    public synchronized <T> T get(DataKey<T> key) {
        Object value = store.get(key);
        if (value == null) {
            throw new IllegalStateException("Nothing stored under key '" + key.name()
                    + "' in this scenario. Did an earlier step run?");
        }
        return key.type().cast(value);
    }

    /**
     * Tells whether a value is stored.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return true if a value was stored
     */
    public synchronized <T> boolean has(DataKey<T> key) {
        return store.containsKey(key);
    }

    /**
     * Adds an item to a list stored under a list key, creating the list on first use:
     * {@code session.data().add(Keys.CREATED_INST_MAN, instMan)}.
     *
     * @param key  a list key, see {@link DataKey#listOf}
     * @param item the item to add
     * @param <E>  the type of the list elements
     * @throws IllegalArgumentException if the item is null
     */
    public synchronized <E> void add(DataKey<List<E>> key, E item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add null to '" + key.name() + "' in this scenario");
        }
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) store.computeIfAbsent(key, k -> new ArrayList<E>());
        list.add(item);
    }

    /**
     * The items added under a list key, oldest first, as a copy.
     *
     * @param key a list key, see {@link DataKey#listOf}
     * @param <E> the type of the list elements
     * @return the items, empty if none were added
     */
    public synchronized <E> List<E> list(DataKey<List<E>> key) {
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) store.get(key);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * The items added under a list key, for steps that need at least one.
     *
     * @param key a list key, see {@link DataKey#listOf}
     * @param <E> the type of the list elements
     * @return the items, oldest first, as a copy
     * @throws IllegalStateException if nothing was added under the key in this scenario
     */
    public <E> List<E> requireList(DataKey<List<E>> key) {
        List<E> list = list(key);
        if (list.isEmpty()) {
            throw new IllegalStateException("Nothing was added to '" + key.name()
                    + "' in this scenario. Did an earlier step run?");
        }
        return list;
    }

    /**
     * Puts an entry in a map stored under a map key, creating the map on first use. An entry with the same key
     * is replaced: {@code session.data().put(Keys.BASE_UNIQUE, "U1", unique)}.
     *
     * @param key      a map key, see {@link DataKey#mapOf}
     * @param entryKey the entry's key
     * @param value    the entry's value
     * @param <K>      the type of the map keys
     * @param <V>      the type of the map values
     * @throws IllegalArgumentException if the entry key or the value is null
     */
    public synchronized <K, V> void put(DataKey<Map<K, V>> key, K entryKey, V value) {
        if (entryKey == null || value == null) {
            throw new IllegalArgumentException("Cannot put a null key or value in '" + key.name()
                    + "' in this scenario (key " + entryKey + ", value " + value + ")");
        }
        @SuppressWarnings("unchecked")
        Map<K, V> map = (Map<K, V>) store.computeIfAbsent(key, k -> new LinkedHashMap<K, V>());
        map.put(entryKey, value);
    }

    /**
     * The entries put under a map key, in the order they were first put, as a read-only copy.
     *
     * @param key a map key, see {@link DataKey#mapOf}
     * @param <K> the type of the map keys
     * @param <V> the type of the map values
     * @return the entries, empty if none were put in this scenario
     */
    public synchronized <K, V> Map<K, V> map(DataKey<Map<K, V>> key) {
        @SuppressWarnings("unchecked")
        Map<K, V> map = (Map<K, V>) store.get(key);
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
