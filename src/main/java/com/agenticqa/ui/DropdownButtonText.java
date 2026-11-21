package com.agenticqa.ui;

import java.util.Arrays;

/**
 * The texts of the app's dropdown buttons (dropdownButton in locators/common.json). Clicking one opens its list of
 * {@link DropdownOptionValue}s:
 * <pre>
 * windows.switchToWindow(DropdownButtonText.ADD_TOOL, DropdownOptionValue.PRICE_STACK);
 * pages.home().shared().dropdownButton(DropdownButtonText.ADD_TOOL).click();
 * </pre>
 * Adding a dropdown: add a value with its exact text.
 */
public enum DropdownButtonText {

    /** The home page's dropdown button. */
    ADD_TOOL("Add tool"),
    /** The Price Stack window's dropdown button. */
    PRICE_STACK("Price Stack"),
    /** The Customer window's dropdown button. */
    CUSTOMER_WINDOW("Customer Window");

    private final String text;

    DropdownButtonText(String text) {
        this.text = text;
    }

    /** @return the button's text, exactly as shown */
    public String text() {
        return text;
    }

    /**
     * The value with this exact text, for steps that get the text from a feature file.
     *
     * @param text the button's text, for example "Add tool"
     * @return the value
     * @throws IllegalArgumentException if no value has that text (the message lists the known texts)
     */
    public static DropdownButtonText of(String text) {
        return Arrays.stream(values()).filter(b -> b.text.equals(text)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dropdown button '" + text + "'. Known: "
                        + Arrays.stream(values()).map(DropdownButtonText::text).toList()
                        + ". Add it to the DropdownButtonText enum."));
    }

    @Override
    public String toString() {
        return text;
    }
}
