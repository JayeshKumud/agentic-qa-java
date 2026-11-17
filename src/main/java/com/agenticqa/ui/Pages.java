package com.agenticqa.ui;

import com.agenticqa.core.TestSession;

/**
 * The page objects of one scenario, injected into step classes by PicoContainer:
 * <pre>
 * public CustomerUiSteps(TestSession session, Pages pages) { this.pages = pages; }
 *
 * pages.customers().open().search(name);
 * pages.home().selectRole(role);
 * </pre>
 * PicoContainer creates one {@code Pages} per scenario, with that scenario's {@link TestSession}, and gives the
 * same instance to every step class. The page objects themselves are kept in the session
 * ({@link TestSession#pageComponent}), so {@code pages.home()} and {@code PageComponentFactory.get(HomePage.class)}
 * return the same object. A page is created on first use, so API-only scenarios never start a browser.
 * <p>
 * Adding a page: give it a method here that calls {@link #get}, for example
 * {@code public OrdersPage orders() { return get(OrdersPage.class); }}.
 */
public class Pages {

    private final TestSession session;

    /**
     * Receives the scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public Pages(TestSession session) {
        this.session = session;
    }

    /** @return the home page of this scenario */
    public HomePage home() {
        return get(HomePage.class);
    }

    /** @return the customers page of this scenario */
    public CustomersPage customers() {
        return get(CustomersPage.class);
    }

    /**
     * Any page object of this scenario, for pages that have no named method yet.
     *
     * @param type the page class, for example HomePage.class
     * @param <T>  the page type
     * @return the scenario's instance of that page, created on first use
     */
    public <T extends PageComponent> T get(Class<T> type) {
        return PageComponentFactory.get(type, session);
    }
}
