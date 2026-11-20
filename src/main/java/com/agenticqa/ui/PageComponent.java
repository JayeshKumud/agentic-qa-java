package com.agenticqa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;

/**
 * A page object that {@link PageComponentFactory} can create. Implementations need a public constructor
 * {@code (Page page, Healer healer)}, which the factory calls.
 * <p>
 * Actions that are the same on every page, such as {@link #selectDropdownOption}, are written once here as
 * default methods (the help and keyboard shortcuts buttons are in {@link CommonActions}). They use {@link #elements()}, so each runs on the page object's own window, and their locators live
 * once in {@code locators/common.json}.
 */
public interface PageComponent {

    /** @return the title of the browser page */
    String getTitle();

    /**
     * The page object's elements, bound to its own window, so the shared actions below search that window.
     *
     * @return the page object's {@link PageElements}
     */
    PageElements elements();

    /**
     * Opens a dropdown and picks one of its options: clicks the dropdown's button, then the option (the {@code li}
     * in the option list whose span shows {@code option}). The click on the option waits for the list to appear.
     * The button text differs per page, for example "Add Master" on the home page, "Price" in the Price window,
     * "Customer" on the customers page. If the list closes again before the option is clicked, the button is
     * clicked once more and the option again. Locators: dropdownButton, dropdownOption in locators/common.json.
     *
     * @param dropdown the text of the dropdown's button, exactly as shown, for example "Add Master"
     * @param option   the text of the option, exactly as shown, for example "Price"
     */
    default void selectDropdownOption(String dropdown, String option) {
        try {
            elements().get("dropdownButton", "NAME", dropdown).click();
            elements().get("dropdownOption", "OPTION", option).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (PlaywrightException listClosedTooSoon) {
            elements().get("dropdownButton", "NAME", dropdown).click();
            elements().get("dropdownOption", "OPTION", option).click();
        }
    }

    /**
     * Clicks the input of a filter dropdown, found by the label above it ({@code <p class="dropdownV2-label">}),
     * which opens its options (filterDropdownInput in locators/common.json):
     * {@code pricePage.clickFilterDropdown("TEST1")}.
     *
     * @param label the dropdown's label exactly as shown, for example "TEST1" or "TEST2"
     */
    default void clickFilterDropdown(String label) {
        elements().get("filterDropdownInput", "LABEL", label).click();
    }

    /**
     * The text the filter dropdown's input shows, for example "chose one or more options" before anything is
     * chosen.
     *
     * @param label the dropdown's label exactly as shown
     * @return the input's value
     */
    default String filterDropdownValue(String label) {
        return elements().get("filterDropdownInput", "LABEL", label).inputValue();
    }
}
