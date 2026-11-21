package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The elements that are not owned by one page (help button, dropdown, buttons by their text, ...), with one method
 * per element, so callers never pass locator names or placeholders:
 * <pre>
 * pages.home().shared().textButton("Log Out").click();
 * pricePage.shared().dropdownButton("Price Window").click();
 * new SharedElements(page, healer).helpButton().click();
 * </pre>
 * The selectors live once in {@code src/main/resources/locators/common.json}; this class is their only user, as
 * {@link PageElements} is for a page's own file. Each lookup goes through the scenario's healer, so healing and
 * the failure report's element lookups work as for every other element.
 */
public final class SharedElements {

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
