package com.agenticqa.steps;

import com.agenticqa.api.Customer;
import com.agenticqa.api.model.CustomerRequest;
import com.agenticqa.core.DataKey;
import com.microsoft.playwright.APIResponse;

/** Keys for data that several step classes share within one scenario. */
public final class Keys {

    /** The customer the scenario is working with ("the customer" in feature files). */
    public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);

    /** The response of the last API call. */
    public static final DataKey<APIResponse> RESPONSE = DataKey.of("lastResponse", APIResponse.class);

    /** A unique text shared by a batch of customers, so a search finds exactly that batch. */
    public static final DataKey<String> NAME_TAG = DataKey.of("nameTag", String.class);

    /** The zone id set by {@code Given a unique zone id is set}, read by later steps of the same scenario. */
    public static final DataKey<Long> ZONE_ID = DataKey.of("zoneId", Long.class);

    /** A customer request built as a POJO ({@code CustomerPojoSteps}). */
    public static final DataKey<CustomerRequest> CUSTOMER_REQUEST = DataKey.of("customerRequest", CustomerRequest.class);

    /** Constants only, not instantiable. */
    private Keys() {}
}