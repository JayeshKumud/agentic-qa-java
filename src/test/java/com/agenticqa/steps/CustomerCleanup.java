package com.agenticqa.steps;

import com.agenticqa.api.CustomerApi;
import com.agenticqa.core.TestSession;
import io.cucumber.java.After;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers every customer a scenario creates, through the API or the UI, and deletes them after the scenario.
 * PicoContainer gives all step classes of one scenario the same instance.
 */
public class CustomerCleanup {

    private final CustomerApi customers;
    private final Set<Long> ids = new LinkedHashSet<>();

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public CustomerCleanup(TestSession session) {
        this.customers = new CustomerApi(session);
    }

    /**
     * Marks a customer for deletion after the scenario.
     *
     * @param id the customer id
     */
    public void track(long id) {
        ids.add(id);
    }

    /**
     * Unmarks a customer the scenario deleted itself.
     *
     * @param id the customer id
     */
    public void untrack(long id) {
        ids.remove(id);
    }

    /**
     * Deletes the tracked customers. For @After hooks a higher order runs first, so this runs before
     * Hooks.after closes the session (default order 10000).
     */
    @After(order = 20000)
    public void cleanUp() {
        for (Long id : ids) {
            try {
                customers.deleteById(id);
            } catch (RuntimeException ignored) {
                // best effort: cleanup must not hide the real result
            }
        }
    }
}