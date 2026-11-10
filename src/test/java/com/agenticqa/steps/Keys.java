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

    /**
     * The customer request body being built by feature-file steps, as JSON text; it is sent by a later step.
     * Text rather than an object, so a raw body can carry extra fields, wrong types or be malformed.
     */
    public static final DataKey<String> REQUEST = DataKey.of("customerRequest", String.class);

    /** The scenario's random token, substituted for {unique} in feature-file values. */
    public static final DataKey<String> TOKEN = DataKey.of("uniqueToken", String.class);

    /** Constants only, not instantiable. */
    private Keys() {}
}