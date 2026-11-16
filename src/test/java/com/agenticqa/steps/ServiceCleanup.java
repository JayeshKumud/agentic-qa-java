package com.agenticqa.steps;

import com.agenticqa.api.ServiceClient;
import com.agenticqa.core.TestSession;
import io.cucumber.java.After;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Resources a scenario created on any service, deleted after the scenario, pass or fail. They are deleted in
 * reverse order, so a resource that depends on another (an order of a customer) goes first.
 * PicoContainer gives all step classes of one scenario the same instance.
 */
public class ServiceCleanup {

    private final TestSession session;
    private final Deque<String[]> deletes = new ArrayDeque<>();

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public ServiceCleanup(TestSession session) {
        this.session = session;
    }

    /**
     * Registers a DELETE to run after the scenario.
     *
     * @param service the service name
     * @param path    the resource path
     */
    public synchronized void delete(String service, String path) {
        cancel(service, path); // registering the same resource twice deletes it once, at its newest position
        deletes.push(new String[]{service, path});
    }

    /**
     * Unregisters a DELETE, for a resource the scenario already deleted itself, so cleanup does not send a
     * DELETE that can only fail. Does nothing if it was not registered.
     *
     * @param service the service name
     * @param path    the resource path, exactly as registered
     */
    public synchronized void cancel(String service, String path) {
        deletes.removeIf(d -> d[0].equals(service) && d[1].equals(path));
    }

    /** Runs the registered deletes before Hooks.after closes the session (higher order runs first). */
    @After(order = 20000)
    public synchronized void cleanUp() {
        while (!deletes.isEmpty()) {
            String[] delete = deletes.pop();
            try {
                new ServiceClient(session, delete[0]).request("DELETE", delete[1], null, Map.of());
            } catch (RuntimeException ignored) {
                // best effort: cleanup must not hide the real result
            }
        }
    }
}
