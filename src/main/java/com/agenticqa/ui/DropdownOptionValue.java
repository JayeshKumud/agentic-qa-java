package com.agenticqa.ui;

/**
 * The values of the app's dropdown options (dropdownOption in locators/common.json), exactly as shown. Each opens a
 * window whose title is the same text; its page object is in {@code WindowNavigator.WINDOWS}:
 * <pre>
 * windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK);         // -> Price Stack
 * windows.switchToWindow(DropdownButtonText.PRICE_STACK, DropdownOptionValue.CUSTOMER_WINDOW);  // -> Customer
 * </pre>
 * Add tool is only a button ({@link DropdownButtonText#ADD_TOOL}), never an option, so it is not here.
 */
public final class DropdownOptionValue {

    private DropdownOptionValue() {}

    /** Opens the Price Stack window. */
    public static final String PRICE_STACK = "Price Stack";

    /** Opens the Customer window. */
    public static final String CUSTOMER_WINDOW = "Customer Window";
}
