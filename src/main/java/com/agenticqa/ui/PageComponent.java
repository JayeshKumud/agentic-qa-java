package com.agenticqa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

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
     * Opens a dropdown by the text of its button and clicks one of its options: the {@code li} in the option list
     * whose span shows {@code option}. The button text differs per page, for example "Add Master" on the home
     * page, "Price" in the Price window, "Customer" on the customers page. The dropdown can close again right
     * after it opens, so it waits for the option list and opens the dropdown once more if the first click on the
     * option fails. Locators: dropdownButton, dropdownMenu, dropdownOption in locators/common.json.
     *
     * @param dropdown the text of the dropdown's button, exactly as shown, for example "Add Master"
     * @param option   the text of the option, exactly as shown, for example "Price"
     */
    default void selectDropdownOption(String dropdown, String option) {
        try {
            showDropdownOptions(dropdown);
            elements().get("dropdownOption", "OPTION", option).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (PlaywrightException closedTooSoon) {
            showDropdownOptions(dropdown);
            elements().get("dropdownOption", "OPTION", option).click();
        }
    }

    /**
     * Clicks the dropdown's button unless an option list is already open, then waits until the list is visible.
     *
     * @param dropdown the text of the dropdown's button
     */
    private void showDropdownOptions(String dropdown) {
        Locator menu = elements().get("dropdownMenu");
        if (!menu.isVisible()) elements().get("dropdownButton", "NAME", dropdown).click();
        menu.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
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
