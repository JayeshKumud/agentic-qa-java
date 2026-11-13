package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Page object of the customers page ("/"): search form, results table, pagination and the add-customer form.
 * Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/customers.json}, not in this class: a changed id is
 * fixed in that file, and elements are looked up by name when they are used ({@code el.get("searchInput")}).
 * Elements marked to heal there are self-healing locators; the result rows and the empty-result message can
 * legitimately be absent, so they are plain locators.
 * <p>
 * Every action that reloads the page (search, add, paging) waits for the new page to finish loading, so the
 * next read never sees the old document.
 */
public class CustomersPage {

    /** Rows shown per page by the sample app. */
    public static final int PAGE_SIZE = 5;

    /** Name of the locator file, locators/customers.json. */
    public static final String LOCATORS = "customers";

    private final Page page;
    private final PageElements el;

    /**
     * Wraps the scenario's browser page.
     *
     * @param page   the Playwright page (its context has the UI base URL)
     * @param healer this scenario's healer
     */
    public CustomersPage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /**
     * Opens the page.
     *
     * @return this page
     */
    public CustomersPage open() {
        page.navigate("/");
        return this;
    }

    /**
     * Searches by name (the app matches a case-insensitive part of the name).
     *
     * @param text the text to search for
     * @return this page, showing the results
     */
    public CustomersPage search(String text) {
        el.get("searchInput").fill(text);
        clickAndWait("searchButton");
        return this;
    }

    /**
     * Adds a customer through the form. The app redirects back to the unfiltered first page.
     *
     * @param name  the name to type
     * @param email the email to type
     * @return this page, after the redirect
     */
    public CustomersPage addCustomer(String name, String email) {
        el.get("newName").fill(name);
        el.get("newEmail").fill(email);
        clickAndWait("addButton");
        return this;
    }

    /**
     * Goes to the next page of results.
     *
     * @return this page
     */
    public CustomersPage nextPage() {
        clickAndWait("nextLink");
        return this;
    }

    /**
     * Goes to the previous page of results.
     *
     * @return this page
     */
    public CustomersPage previousPage() {
        clickAndWait("previousLink");
        return this;
    }

    /**
     * Clicks an element that loads a new page and waits until that page has loaded.
     * The click itself waits for the navigation it starts; waitForLoadState then waits for the new document's
     * load event. This replaces the deprecated {@code page.waitForNavigation(Runnable)}; waitForURL would not
     * work here because adding a customer can redirect back to the same URL.
     *
     * @param name the locator name in customers.json
     */
    private void clickAndWait(String name) {
        el.get(name).click();
        page.waitForLoadState();
    }

    /**
     * Rows of the results table, for assertions such as hasCount.
     *
     * @return the row locator
     */
    public Locator rows() {
        return el.get("rows");
    }

    /**
     * The "No customers found." cell, shown when nothing matches.
     *
     * @return its locator
     */
    public Locator noResultsMessage() {
        return el.get("noResults");
    }

    /**
     * The "Page X of Y" text.
     *
     * @return its locator
     */
    public Locator pageIndicator() {
        return el.get("pageIndicator");
    }

    /**
     * Reads the rows currently shown.
     *
     * @return one entry per row, top to bottom
     */
    public List<CustomerRow> visibleRows() {
        List<CustomerRow> result = new ArrayList<>();
        for (Locator row : el.get("rows").all()) {
            result.add(new CustomerRow(
                    Long.parseLong(row.getAttribute("data-customer-id")),
                    el.within(row, "rowName").innerText().trim(),
                    el.within(row, "rowEmail").innerText().trim(),
                    el.within(row, "rowStatus").innerText().trim()));
        }
        return result;
    }

    /**
     * One row of the results table.
     *
     * @param id     the customer id (data-customer-id)
     * @param name   the Name column
     * @param email  the Email column
     * @param status the Status column
     */
    public record CustomerRow(long id, String name, String email, String status) {}
}