package com.agenticqa.ui;

import com.agenticqa.ui.LocatorRepository.Definition;
import com.agenticqa.ui.healing.Healer;
import com.agenticqa.ui.healing.SmartLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The elements of one page, looked up by name in {@code locators/PAGE.json} when they are used, so page
 * objects hold no selectors and need no locator fields:
 * <pre>
 * elements.get("searchInput").fill(text);
 * </pre>
 * Elements marked {@code heal} in the file are resolved through the scenario's {@link Healer} on every call
 * (so a renamed id heals), the others are plain Playwright locators.
 */
public final class PageElements {

    private final Page page;
    private final Healer healer;
    private final String pageName;

    /**
     * Binds a locator file to the scenario's page.
     *
     * @param page     the Playwright page
     * @param healer   the scenario's healer
     * @param pageName the locator file name without .json, for example "customers"
     * @throws IllegalArgumentException if the file does not exist or is malformed (checked now, not on first use)
     */
    public PageElements(Page page, Healer healer, String pageName) {
        this.page = page;
        this.healer = healer;
        this.pageName = pageName;
        LocatorRepository.page(pageName);
    }

    /**
     * Finds an element now.
     *
     * @param name the locator name in the file
     * @return the locator, healed if needed
     * @throws IllegalArgumentException if the file has no locator with that name
     */
    public Locator get(String name) {
        Definition definition = LocatorRepository.get(pageName, name);
        if (!definition.heal()) return page.locator(definition.selector());
        return new SmartLocator(page, healer, definition.key(), definition.description(), definition.selector())
                .locator();
    }

    /**
     * Finds an element whose selector has a placeholder, for example {@code button.role_login_ROLE}, so one
     * entry in the file serves every role or row: {@code el.get("roleLogin", "ROLE", "COE")}.
     * The healing key includes the value, so each role keeps its own fingerprint and a healed lookup can
     * never return another role's element.
     *
     * @param name        the locator name in the file
     * @param placeholder the text in the selector to replace, for example "ROLE" or "MYROW"
     * @param value       what to put in its place
     * @return the locator, healed if needed
     * @throws IllegalArgumentException if the file has no locator with that name, or its selector does not
     *                                  contain the placeholder
     */
    public Locator get(String name, String placeholder, String value) {
        Definition definition = LocatorRepository.get(pageName, name);
        if (!definition.selector().contains(placeholder)) {
            throw new IllegalArgumentException("Locator '" + name + "' in locators/" + pageName
                    + ".json has no placeholder '" + placeholder + "': " + definition.selector());
        }
        String selector = definition.selector().replace(placeholder, value);
        if (!definition.heal()) return page.locator(selector);
        return new SmartLocator(page, healer, definition.key() + "." + value,
                definition.description().replace(placeholder, value), selector).locator();
    }

    /**
     * Finds an element inside another one, for example a cell inside a table row.
     *
     * @param parent the outer element
     * @param name   the locator name of the inner element
     * @return the inner locator (never healed: inner selectors are relative)
     */
    public Locator within(Locator parent, String name) {
        return parent.locator(LocatorRepository.get(pageName, name).selector());
    }
}
