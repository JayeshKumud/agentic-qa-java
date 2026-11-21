package com.agenticqa.ui;

import java.util.Arrays;

/**
 * The values of the app's dropdown options (dropdownOption in locators/common.json). Each opens a window whose title
 * is the same text, and {@link #window()} is that window's page object:
 * <pre>
 * windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK);         // -> Price Stack
 * windows.switchToWindow(DropdownButtonText.PRICE_STACK, DropdownOptionValue.CUSTOMER_WINDOW);  // -> Customer
 * </pre>
 * Add tool is only a button ({@link DropdownButtonText#ADD_TOOL}), never an option, so it is not here.
 * Adding a window: add a value with its exact text and page object.
 */
public enum DropdownOptionValue {

    /** Opens the Price Stack window. */
    PRICE_STACK("Price Stack", PricePage.class),
    /** Opens the Customer window. */
    CUSTOMER_WINDOW("Customer Window", CustomersPage.class);

    private final String value;
    private final Class<? extends PageComponents> window;

    DropdownOptionValue(String value, Class<? extends PageComponents> window) {
        this.value = value;
        this.window = window;
    }

    /** @return the option's text, exactly as shown, which is also the title of the window it opens */
    public String text() {
        return value;
    }

    /** @return the page object of the window this option opens, for example PricePage.class */
    public Class<? extends PageComponents> window() {
        return window;
    }

    /**
     * The value with this exact text, for steps that get the text from a feature file.
     *
     * @param text the option's text, for example "Price Stack"
     * @return the value
     * @throws IllegalArgumentException if no value has that text (the message lists the known texts)
     */
    public static DropdownOptionValue of(String text) {
        return Arrays.stream(values()).filter(o -> o.value.equals(text)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dropdown option '" + text + "'. Known: "
                        + Arrays.stream(values()).map(DropdownOptionValue::text).toList()
                        + ". Add it to the DropdownOptionValue enum."));
    }

    @Override
    public String toString() {
        return value;
    }
}
