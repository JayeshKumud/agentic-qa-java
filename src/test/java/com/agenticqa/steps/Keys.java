package com.agenticqa.steps;

import com.agenticqa.apis.model.Customer;
import com.agenticqa.apis.model.Unique;
import com.agenticqa.core.DataKey;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

/** Keys for data that several step classes share within one scenario. */
public final class Keys {

    /** The customer the scenario is working with ("the customer" in feature files), for GUI and API steps alike. */
    public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);

    /** The REST Assured response of the last API call. */
    public static final DataKey<Response> RESPONSE = DataKey.of("lastResponse", Response.class);

    /** A unique text shared by a batch of customers, so a search finds exactly that batch. */
    public static final DataKey<String> NAME_TAG = DataKey.of("nameTag", String.class);

    /** Path of the base data: testdata.baseDataPrefix + the BaseData value of the test data table. */
    public static final DataKey<String> BASE_DATA_PATH = DataKey.of("baseDataPath", String.class);

    /** Path of the scenario data: testdata.scenarioPrefix + the Scenario value of the test data table. */
    public static final DataKey<String> SCENARIO_PATH = DataKey.of("scenarioPath", String.class);

    /** The zone id set by {@code Given a unique zone id is set}, read by later steps of the same scenario. */
    public static final DataKey<Long> ZONE_ID = DataKey.of("zoneId", Long.class);

    /**
     * The views of the scenario, shared between steps. Store a copy so later changes to the caller's list
     * cannot change it: {@code session.put(VIEWS, List.copyOf(views))}, read with {@code session.get(VIEWS)}.
     */
    public static final DataKey<List<String>> VIEWS = DataKey.listOf("views", String.class);

    /**
     * Uniques created in the scenario, by base name. Add with {@code TestSession.current().put(BASE_UNIQUE, name,
     * unique)} and read with {@code TestSession.current().map(BASE_UNIQUE)}; see {@code BackUniqueTransit}.
     * The constant is only the key: the map itself lives in each scenario's session and ends with it.
     */
    public static final DataKey<Map<String, Unique>> BASE_UNIQUE =
            DataKey.mapOf("baseUnique", String.class, Unique.class);

    /** Constants only, not instantiable. */
    private Keys() {}
}