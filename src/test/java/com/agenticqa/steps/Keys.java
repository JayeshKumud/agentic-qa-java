package com.agenticqa.steps;

import com.agenticqa.apis.model.Customer;
import com.agenticqa.core.DataKey;
import io.restassured.response.Response;

import java.util.List;

/** Keys for data that several step classes share within one scenario. */
public final class Keys {

    /** The customer the scenario is working with ("the customer" in feature files), for GUI and API steps alike. */
    public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);

    /** The REST Assured response of the last API call. */
    public static final DataKey<Response> RESPONSE = DataKey.of("lastResponse", Response.class);

    /** A unique text shared by a batch of customers, so a search finds exactly that batch. */
    public static final DataKey<String> NAME_TAG = DataKey.of("nameTag", String.class);

    /** The zone id set by {@code Given a unique zone id is set}, read by later steps of the same scenario. */
    public static final DataKey<Long> ZONE_ID = DataKey.of("zoneId", Long.class);

    /**
     * The views of the scenario, shared between steps. Store a copy so later changes to the caller's list
     * cannot change it: {@code session.put(VIEWS, List.copyOf(views))}, read with {@code session.get(VIEWS)}.
     */
    public static final DataKey<List<String>> VIEWS = DataKey.listOf("views", String.class);

    /** Constants only, not instantiable. */
    private Keys() {}
}