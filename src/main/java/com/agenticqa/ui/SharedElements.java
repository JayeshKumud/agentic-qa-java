package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The elements that are not owned by one page (help button, dropdown, buttons by their text, ...), with one method
 * per element, so callers never pass locator names or placeholders. The app's fixed texts are defined here once
 * ({@link #LOG_OUT}, {@link #ADD_TOOL}, ...), each with its own method; the methods that take a text are for
 * values that vary, such as a role:
 * <pre>
 * pages.home().shared().logoutButton().click();
 * pages.home().shared().addMasterButton().click();
 * pages.home().shared().textButton(role).click();
 * new SharedElements(page, healer).helpButton().click();
 * </pre>
 * The selectors live once in {@code src/main/resources/locators/common.json}; this class is their only user, as
 * {@link PageElements} is for a page's own file. Each lookup goes through the scenario's healer, so healing and
 * the failure report's element lookups work as for every other element.
 */
public final class SharedElements {

    /** Text of the log out button (home page). */
    public static final String LOG_OUT = "Log Out";
    /** Text of the home page's dropdown button that opens the other windows. */
    public static final String ADD_TOOL = "Add tool";
    /** The Price window: an option of the Add Master dropdown, the window's title, and its own dropdown button. */
    public static final String PRICE_STACK = "Price Stack";
    /** The Customer window: an option of the Price window's dropdown, and the window's title. */
    public static final String CUSTOMER_WINDOW = "Customer Window";

    private final PageElements el;

    /**
     * Binds the shared locators to a window.
     *
     * @param page   the window to find the elements in
     * @param healer this scenario's healer
     */
    public SharedElements(Page page, Healer healer) {
        this.el = new PageElements(page, healer, LocatorRepository.COMMON);
    }

    /** @return the help button (helpButton) */
    public Locator helpButton() {
        return el.get("helpButton");
    }

    /** @return the keyboard shortcuts button (keyboardShortcutsButton) */
    public Locator keyboardShortcutsButton() {
        return el.get("keyboardShortcutsButton");
    }

    /** @return the log out button of the home page: {@code textButton(LOG_OUT)} */
    public Locator logoutButton() {
        return textButton(LOG_OUT);
    }

    /** @return the role button of the home page: {@code textButton(LOG_OUT)} */
    public Locator roleButton(String role) {
        return textButton(role);
    }

    /** @return the home page's Add Master dropdown button: {@code dropdownButton(ADD_MASTER)} */
    public Locator addMasterButton() {
        return dropdownButton(ADD_TOOL);
    }

    /** @return the Price window's own dropdown button: {@code dropdownButton(PRICE_WINDOW)} */
    public Locator priceWindowButton() {
        return dropdownButton(PRICE_STACK);
    }

    /** @return the Price Window option of the open dropdown: {@code dropdownOption(PRICE_WINDOW)} */
    public Locator priceWindowOption() {
        return dropdownOption(PRICE_STACK);
    }

    /** @return the Customer Window option of the open dropdown: {@code dropdownOption(CUSTOMER_WINDOW)} */
    public Locator customerWindowOption() {
        return dropdownOption(CUSTOMER_WINDOW);
    }

    /**
     * The {@code button type="button" data-testid="button"} whose {@code div} shows a text (textButton), for
     * example "Log Out" or a role such as "COE".
     *
     * @param text the text, exactly as shown
     * @return the button
     */
    public Locator textButton(String text) {
        return el.get("textButton", "TEXT", text);
    }

    /**
     * A dropdown's button, by its text (dropdownButton), for example "Add Master" or "Price Window".
     *
     * @param name the button's text, exactly as shown
     * @return the button
     */
    public Locator dropdownButton(String name) {
        return el.get("dropdownButton", "NAME", name);
    }

    /**
     * An option of the open dropdown, by its text (dropdownOption), for example "Price Window".
     *
     * @param option the option's text, exactly as shown
     * @return the option
     */
    public Locator dropdownOption(String option) {
        return el.get("dropdownOption", "OPTION", option);
    }

    /**
     * The input of a filter dropdown, by the label above it (filterDropdownInput), for example "TEST1".
     *
     * @param label the label, exactly as shown
     * @return the input
     */
    public Locator filterDropdownInput(String label) {
        return el.get("filterDropdownInput", "LABEL", label);
    }
}
