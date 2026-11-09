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

    /** Constants only, not instantiable. */
    private Keys() {}
}