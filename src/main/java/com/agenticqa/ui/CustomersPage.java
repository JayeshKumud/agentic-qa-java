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
 * The selectors live in several locator files under {@code src/main/resources/locators}, one per area of the
 * page, not in this class:
 * <ul>
 *   <li>{@code customers.json}: search and results table</li>
 *   <li>{@code customer-form.json}: the add-customer form</li>
 *   <li>{@code pagination.json}: paging links, shared with other list pages</li>
 *   <li>{@code common.json}: elements every page has (added by {@link BasePage})</li>
 * </ul>
 * Elements are looked up by name when they are used. Elements marked to heal there are self-healing locators;
 * the result rows and the empty-result message can legitimately be absent, so they are plain locators.
 * <p>
 * Every action that reloads the page (search, add, paging) waits for the new page to finish loading
 * ({@link #submit}), so the next read never sees the old document.
 */
public class CustomersPage extends BasePage<CustomersPage> {

    /** Rows shown per page by the sample app. */
    public static final int PAGE_SIZE = 5;

    /** Locator files of this page, most specific first; {@value BasePage#COMMON}.json is added by BasePage. */
    public static final List<String> LOCATORS = List.of("customers", "customer-form", "pagination");

    /**
     * Wraps the scenario's browser page.
     *
     * @param page   the Playwright page (its context has the UI base URL)
     * @param healer this scenario's healer
     */
    public CustomersPage(Page page, Healer healer) {
        super(page, healer, LOCATORS.toArray(String[]::new));
    }

    @Override
    protected String path() {
        return "/";
    }

    /**
     * Searches by name (the app matches a case-insensitive part of the name).
     *
     * @param text the text to search for
     * @return this page, showing the results
     */
    public CustomersPage search(String text) {
        fill("searchInput", text);
        return submit("searchButton");
    }

    /**
     * Adds a customer through the form. The app redirects back to the unfiltered first page.
     *
     * @param name  the name to type
     * @param email the email to type
     * @return this page, after the redirect
     */
    public CustomersPage addCustomer(String name, String email) {
        fill("newName", name);
        fill("newEmail", email);
        return submit("addButton");
    }

    /**
     * Goes to the next page of results.
     *
     * @return this page
     */
    public CustomersPage nextPage() {
        return submit("pagination.nextLink");
    }

    /**
     * Goes to the previous page of results.
     *
     * @return this page
     */
    public CustomersPage previousPage() {
        return submit("pagination.previousLink");
    }

    /**
     * Rows of the results table, for assertions such as hasCount.
     *
     * @return the row locator
     */
    public Locator rows() {
        return find("rows");
    }

    /**
     * The "No customers found." cell, shown when nothing matches.
     *
     * @return its locator
     */
    public Locator noResultsMessage() {
        return find("noResults");
    }

    /**
     * The "Page X of Y" text.
     *
     * @return its locator
     */
    public Locator pageIndicator() {
        return find("pageIndicator");
    }

    /**
     * Reads the rows currently shown.
     *
     * @return one entry per row, top to bottom
     */
    public List<CustomerRow> visibleRows() {
        List<CustomerRow> result = new ArrayList<>();
        for (Locator row : find("rows").all()) {
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
