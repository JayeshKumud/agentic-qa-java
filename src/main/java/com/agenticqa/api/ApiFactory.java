package com.agenticqa.api;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates API classes and hands out the same instance on every call, in the style of the existing framework:
 * <pre>
 * Response response = ApiFactory.getApi(CustomerValidationApi.class).postCustomer(body);
 * </pre>
 * An API class is created on first use through its no-argument constructor and kept for the whole run, shared
 * by every scenario and thread. That is safe because an {@link Api} holds only final settings (base URL, paths)
 * and no per-scenario state.
 */
public class ApiFactory {

    private ApiFactory() {}

    static private final List<Api> createdApis = new CopyOnWriteArrayList<>();

    /**
     * The instance of an API class, created on first use.
     *
     * @param apiToCreate the API class, for example CustomerValidationApi.class
     * @param <T>         the API type
     * @return the shared instance of that API class
     * @throws RuntimeException if the class cannot be created (no public no-argument constructor, or it failed)
     */
    public static <T extends Api> T getApi(Class<T> apiToCreate) {
        for (Api api : createdApis) {
            if (api.getClass().equals(apiToCreate)) {
                return apiToCreate.cast(api);
            }
        }

        T apiInstance;

        try {

            apiInstance = apiToCreate.getDeclaredConstructor().newInstance();
            createdApis.add(apiInstance);

        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Unable to create api instance: " + apiToCreate.getName(), e);
        }

        return apiInstance;
    }
}
