package com.agenticqa.ui;

/**
 * The texts of the app's dropdown buttons (dropdownButton in locators/common.json), exactly as shown. Clicking one
 * opens its list of {@link DropdownOptionValue}s:
 * <pre>
 * windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK);
 * pages.home().shared().dropdownButton(DropdownButtonText.ADD_TOOL).click();
 * </pre>
 */
public final class DropdownButtonText {

    private DropdownButtonText() {}

    /** The home page's dropdown button. Only a button, never an option. */
    public static final String ADD_TOOL = "Add tool";

    /** The Price Stack window's dropdown button. */
    public static final String PRICE_STACK = "Price Stack";

    /** The Customer window's dropdown button. */
    public static final String CUSTOMER_WINDOW = "Customer Window";
}
