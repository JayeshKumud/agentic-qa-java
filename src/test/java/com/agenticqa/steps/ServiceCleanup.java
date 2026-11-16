package com.agenticqa.steps;

import io.cucumber.java.After;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data a scenario created, deleted after the scenario, pass or fail, newest first (so a resource that depends on
 * another goes first). PicoContainer gives all step classes of one scenario the same instance:
 * <pre>
 * cleanup.add("customer " + id, () -> ApiFactory.getApi(CustomerApi.class).deleteCustomer(id));
 * </pre>
 */
public class ServiceCleanup {

    private final Map<String, Runnable> deletes = new LinkedHashMap<>();

    /**
     * Registers a delete to run after the scenario. Registering the same key again replaces the earlier delete,
     * so a resource is deleted once.
     *
     * @param key    names the resource, for example "customer 42"
     * @param delete deletes it, for example through an API class
     */
    public synchronized void add(String key, Runnable delete) {
        deletes.remove(key);
        deletes.put(key, delete);
    }

    /**
     * Unregisters a delete, for a resource the scenario already deleted itself. Does nothing if it was not
     * registered.
     *
     * @param key the key it was registered with
     */
    public synchronized void cancel(String key) {
        deletes.remove(key);
    }

    /** Runs the registered deletes, newest first, before Hooks.after closes the session (higher order runs first). */
    @After(order = 20000)
    public synchronized void cleanUp() {
        String[] keys = deletes.keySet().toArray(new String[0]);
        for (int i = keys.length - 1; i >= 0; i--) {
            try {
                deletes.get(keys[i]).run();
            } catch (RuntimeException | AssertionError ignored) {
                // best effort: cleanup must not hide the real result (API classes fail with Assert.fail)
            }
        }
        deletes.clear();
    }
}
