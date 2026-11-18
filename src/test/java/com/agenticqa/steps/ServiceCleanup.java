package com.agenticqa.steps;

import io.cucumber.java.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(ServiceCleanup.class);

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

    /**
     * Runs the registered deletes, newest first. Order 2: after TestInitializeHooks has captured the failure
     * evidence and closed the browser (3 and up: closeBrowser 4, cleanVideoFolder 3), before teardownData deletes the zone and records the result (1).
     * For {@code @After} hooks a higher order runs first.
     */
    @After(order = 2)
    public synchronized void cleanUp() {
        String[] keys = deletes.keySet().toArray(new String[0]);
        for (int i = keys.length - 1; i >= 0; i--) {
            try {
                deletes.get(keys[i]).run();
                logger.info("Cleanup: deleted {}", keys[i]);
            } catch (RuntimeException | AssertionError e) {
                // best effort: cleanup must not hide the real result (API classes fail with Assert.fail)
                logger.warn("Cleanup: could not delete {}: {}", keys[i], e.getMessage());
            }
        }
        deletes.clear();
    }
}
