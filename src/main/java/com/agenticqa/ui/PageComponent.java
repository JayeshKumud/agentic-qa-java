package com.agenticqa.ui;

/**
 * A page object that {@link PageComponentFactory} can create. Implementations need a public constructor
 * {@code (Page page, Healer healer)}, which the factory calls.
 * <p>
 * Actions that are the same on every page, such as {@link #openHelp()}, are written once here as default
 * methods. They use {@link #elements()}, so each runs on the page object's own window, and their locators live
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

    /** Clicks the help button, which every page has (helpButton in locators/common.json). */
    default void openHelp() {
        elements().get("helpButton").click();
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
