package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.agenticqa.ui.healing.SmartLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Page object of the customers page ("/"): search form, results table, pagination and the add-customer form.
 * Steps talk to this class and never use selectors themselves, so a changed id is fixed in one place.
 * <p>
 * Every action that reloads the page (search, add, paging) waits for the new page to finish loading, so the
 * next read never sees the old document.
 * <p>
 * Elements that are always on the page are {@link SmartLocator}s and heal themselves if their id changes;
 * the result rows and the empty-result message can legitimately be absent, so they stay plain locators.
 */
public class CustomersPage {

    /** Rows shown per page by the sample app. */
    public static final int PAGE_SIZE = 5;

    private final Page page;
    private final SmartLocator searchInput;
    private final SmartLocator searchButton;
    private final Locator rows;
    private final Locator noResults;
    private final SmartLocator pageIndicator;
    private final SmartLocator nextLink;
    private final SmartLocator previousLink;
    private final SmartLocator newName;
    private final SmartLocator newEmail;
    private final SmartLocator addButton;

    /**
     * Wraps the scenario's browser page.
     *
     * @param page   the Playwright page (its context has the UI base URL)
     * @param healer this scenario's healer
     */
    public CustomersPage(Page page, Healer healer) {
        this.page = page;
        this.searchInput = new SmartLocator(page, healer, "customers.searchInput", "search input", "#search-input");
        this.searchButton = new SmartLocator(page, healer, "customers.searchButton", "search button", "#search-button");
        this.rows = page.locator("#customers-table tr.customer-row");
        this.noResults = page.locator("#no-results");
        this.pageIndicator = new SmartLocator(page, healer, "customers.pageIndicator", "page indicator", "#page-indicator");
        this.nextLink = new SmartLocator(page, healer, "customers.nextLink", "next page link", "#next-page-link");
        this.previousLink = new SmartLocator(page, healer, "customers.previousLink", "previous page link", "#prev-page-link");
        this.newName = new SmartLocator(page, healer, "customers.newName", "new customer name input", "#new-customer-name");
        this.newEmail = new SmartLocator(page, healer, "customers.newEmail", "new customer email input", "#new-customer-email");
        this.addButton = new SmartLocator(page, healer, "customers.addButton", "add customer button", "#add-customer-button");
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
        searchInput.locator().fill(text);
        page.waitForNavigation(searchButton.locator()::click);
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
        newName.locator().fill(name);
        newEmail.locator().fill(email);
        page.waitForNavigation(addButton.locator()::click);
        return this;
    }

    /**
     * Goes to the next page of results.
     *
     * @return this page
     */
    public CustomersPage nextPage() {
        page.waitForNavigation(nextLink.locator()::click);
        return this;
    }

    /**
     * Goes to the previous page of results.
     *
     * @return this page
     */
    public CustomersPage previousPage() {
        page.waitForNavigation(previousLink.locator()::click);
        return this;
    }

    /**
     * Rows of the results table, for assertions such as hasCount.
     *
     * @return the row locator
     */
    public Locator rows() {
        return rows;
    }

    /**
     * The "No customers found." cell, shown when nothing matches.
     *
     * @return its locator
     */
    public Locator noResultsMessage() {
        return noResults;
    }

    /**
     * The "Page X of Y" text.
     *
     * @return its locator
     */
    public Locator pageIndicator() {
        return pageIndicator.locator();
    }

    /**
     * Reads the rows currently shown.
     *
     * @return one entry per row, top to bottom
     */
    public List<CustomerRow> visibleRows() {
        List<CustomerRow> result = new ArrayList<>();
        for (Locator row : rows.all()) {
            result.add(new CustomerRow(
                    Long.parseLong(row.getAttribute("data-customer-id")),
                    row.locator(".col-name").innerText().trim(),
                    row.locator(".col-email").innerText().trim(),
                    row.locator(".col-status").innerText().trim()));
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