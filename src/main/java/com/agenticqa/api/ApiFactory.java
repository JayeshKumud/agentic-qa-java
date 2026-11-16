package com.agenticqa.api;

import com.agenticqa.core.TestSession;

import java.lang.reflect.InvocationTargetException;

/**
 * Creates API clients for the running scenario and hands out the same instance on every call, from anywhere:
 * <pre>
 * CustomerApi customers = ApiFactory.getApi(CustomerApi.class);
 * APIResponse created = ApiFactory.getApi(EndpointClient.class).call("customers.create", payload);
 * </pre>
 * A client is created on first use, through its {@code (TestSession)} constructor, with the scenario's session,
 * so it shares the browser's cookies, records its calls in the scenario's call log and uses the scenario's
 * environment settings.
 * <p>
 * The factory keeps no state: it finds the scenario with {@link TestSession#current()}, and the created clients
 * live in that session. They are scenario-scoped, not shared by the whole run, because each client holds its
 * scenario's session: a client kept for the whole run would send the next scenario's calls through a closed
 * browser context and into the wrong call log. Parallel scenarios therefore never share a client.
 * <p>
 * {@link ServiceClient} is not created here: it needs a service name as well, so code keeps creating it with
 * {@code new ServiceClient(session, "orders")}.
 */
public final class ApiFactory {

    private ApiFactory() {}

    /**
     * The API client of a type, created on first use in this scenario.
     *
     * @param apiToCreate the client class, for example CustomerApi.class
     * @param <T>         the client type
     * @return the scenario's instance of that client
     * @throws IllegalStateException if no scenario is running on this thread, or the client cannot be created
     */
    public static <T extends Api> T getApi(Class<T> apiToCreate) {
        TestSession session = TestSession.current();
        return session.scenarioObject(apiToCreate, () -> create(apiToCreate, session));
    }

    /**
     * Calls the client's {@code (TestSession)} constructor.
     *
     * @param apiToCreate the client class
     * @param session     the scenario's session
     * @param <T>         the client type
     * @return the new client
     */
    private static <T extends Api> T create(Class<T> apiToCreate, TestSession session) {
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
