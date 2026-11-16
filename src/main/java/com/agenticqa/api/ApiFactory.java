package com.agenticqa.api;

import com.agenticqa.core.TestSession;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates the API clients of one scenario and hands out the same instance on every call. Step classes receive it
 * from PicoContainer, like {@link TestSession} and {@code PageComponentFactory}:
 * <pre>
 * public OrderSteps(ApiFactory apis) {
 *     this.customers = apis.getApi(CustomerApi.class);
 * }
 * </pre>
 * A client is created on first use, through its {@code (TestSession)} constructor, with the scenario's session,
 * so it shares the browser's cookies, records its calls in the scenario's call log and uses the scenario's
 * environment settings. Creating a client does not start a browser; its first call does.
 * <p>
 * PicoContainer creates one factory per scenario and gives every step class of that scenario the same one, then
 * discards it. The clients are therefore scenario-scoped, not shared by the whole run: each one holds its
 * scenario's session, so a client kept for the whole run would send the next scenario's calls through a closed
 * browser context and into the wrong call log. No ThreadLocal or cleanup in the hooks is needed.
 * <p>
 * {@link ServiceClient} is not created here: it needs a service name as well, so code keeps creating it with
 * {@code new ServiceClient(session, "orders")}.
 */
public class ApiFactory {

    private final TestSession session;
    private final Map<Class<? extends Api>, Api> createdApis = new HashMap<>();

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public ApiFactory(TestSession session) {
        this.session = session;
    }

    /**
     * The API client of a type, created on first use in this scenario.
     *
     * @param apiToCreate the client class, for example CustomerApi.class
     * @param <T>         the client type
     * @return the scenario's instance of that client
     * @throws IllegalStateException if the client cannot be created
     */
    public synchronized <T extends Api> T getApi(Class<T> apiToCreate) {
        Api existing = createdApis.get(apiToCreate);
        if (existing != null) return apiToCreate.cast(existing);

        T api = create(apiToCreate);
        createdApis.put(apiToCreate, api);
        return api;
    }

    /**
     * Calls the client's {@code (TestSession)} constructor with the scenario's session.
     *
     * @param apiToCreate the client class
     * @param <T>         the client type
     * @return the new client
     */
    private <T extends Api> T create(Class<T> apiToCreate) {
        try {
            return apiToCreate.getConstructor(TestSession.class).newInstance(session);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(apiToCreate.getSimpleName()
                    + " needs a public constructor (TestSession session)", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("The constructor of " + apiToCreate.getSimpleName() + " failed: "
                    + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create api instance: " + apiToCreate.getName(), e);
        }
    }
}
