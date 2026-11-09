package com.agenticqa.ui.healing;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * An element of a page object that heals itself when its selector stops matching. Call {@link #locator()} each
 * time the element is used (not once in a constructor), so the lookup happens on the current page.
 * <p>
 * Use it only for elements that must always be present (inputs, buttons, links, headings). Lists and messages
 * that are legitimately absent, such as an empty-result text, should stay plain Playwright locators;
 * otherwise their absence would trigger a search for a look-alike.
 */
public final class SmartLocator {

    private final Page page;
    private final Healer healer;
    private final String key;
    private final String description;
    private final String selector;

    /**
     * Defines an element.
     *
     * @param page        the page it lives on
     * @param healer      this scenario's healer
     * @param key         stable unique key, for example "customers.searchInput"; it names the stored fingerprint
     * @param description readable name used in logs and reports, for example "search input"
     * @param selector    the selector to try first
     */
    public SmartLocator(Page page, Healer healer, String key, String description, String selector) {
        this.page = page;
        this.healer = healer;
        this.key = key;
        this.description = description;
        this.selector = selector;
    }

    /**
     * Finds the element now, healing the selector if needed.
     *
     * @return a Playwright locator for the element
     */
    public Locator locator() {
        return healer.resolve(page, this);
    }

    /** @return the stable key of the element */
    public String key() {
        return key;
    }

    /** @return the readable name of the element */
    public String description() {
        return description;
    }

    /** @return the selector written in the page object */
    public String selector() {
        return selector;
    }
}
