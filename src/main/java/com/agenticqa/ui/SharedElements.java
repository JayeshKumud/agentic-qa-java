package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The elements that are not owned by one page (help button, dropdown, buttons by their text, ...), with one method
 * per element, so callers never pass locator names or placeholders. The standard dropdown texts are the
 * {@link DropdownButtonText} and {@link DropdownOptionValue} enums; the methods that take a text are for values that
 * vary, such as a role:
 * <pre>
 * pages.home().shared().logoutButton().click();
 * pages.home().shared().dropdownButton(DropdownButtonText.ADD_TOOL).click();
 * pages.home().shared().roleButton(role).click();
 * new SharedElements(page, healer).helpButton().click();
 * </pre>
 * The selectors live once in {@code src/main/resources/locators/common.json}; this class is their only user, as
 * {@link PageElements} is for a page's own file. Each lookup goes through the scenario's healer, so healing and
 * the failure report's element lookups work as for every other element.
 */
public final class SharedElements {

    /** Text of the log out button (home page). */
    public static final String LOG_OUT = "Log Out";

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

    /**
     * The role button of the home page, shown after a role is selected: {@code textButton(role)}.
     *
     * @param role the role, for example "COE"
     * @return the button
     */
    public Locator roleButton(String role) {
        return textButton(role);
    }

    /**
     * A standard dropdown's button: {@code dropdownButton(DropdownButtonText.ADD_TOOL)}.
     *
     * @param button the button's text
     * @return the button
     */
    public Locator dropdownButton(DropdownButtonText button) {
        return dropdownButton(button.text());
    }

    /**
     * A standard option of the open dropdown: {@code dropdownOption(DropdownOptionValue.PRICE_STACK)}.
     *
     * @param option the option's value
     * @return the option
     */
    public Locator dropdownOption(DropdownOptionValue option) {
        return dropdownOption(option.text());
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
     * A dropdown's button, by its text (dropdownButton), for example "Add tool" or "Price Stack".
     *
     * @param name the button's text, exactly as shown
     * @return the button
     */
    public Locator dropdownButton(String name) {
        return el.get("dropdownButton", "NAME", name);
    }

    /**
     * An option of the open dropdown, by its text (dropdownOption), for example "Price Stack".
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
