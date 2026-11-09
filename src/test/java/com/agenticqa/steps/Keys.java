package com.agenticqa.steps;

import com.agenticqa.api.Customer;
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

    /** The customer request being built by feature-file steps; it is sent by a later step. */
    public static final DataKey<Customer> REQUEST = DataKey.of("customerRequest", Customer.class);

    /** The scenario's random token, substituted for {unique} in feature-file values. */
    public static final DataKey<String> TOKEN = DataKey.of("uniqueToken", String.class);

    /** Constants only, not instantiable. */
    private Keys() {}
}