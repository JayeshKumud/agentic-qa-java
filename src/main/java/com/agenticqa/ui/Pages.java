package com.agenticqa.ui;

import com.agenticqa.core.TestSession;

/**
 * The page objects of one scenario, injected into step classes by PicoContainer:
 * <pre>
 * public CustomerUiSteps(TestSession session, Pages pages) { this.pages = pages; }
 *
 * pages.customers().open().search(name);
 * pages.home().selectRole(role);
 * pages.klm().open();
 * </pre>
 * PicoContainer creates one {@code Pages} per scenario, with that scenario's {@link TestSession}, and gives the
 * same instance to every step class. The page objects themselves are kept in the session
 * ({@link TestSession#pageComponent}), so {@code pages.home()} and {@code PageComponentsFactory.get(HomePage.class)}
 * return the same object. A page is created on first use, so API-only scenarios never start a browser.
 * <p>
 * Moving between windows is {@link WindowNavigator}'s job; after it opened a window, the method here for that
 * window ({@code price()}, {@code customers()}) returns the page object of that window.
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

    /** @return the login page of this scenario (the first window) */
    public LoginPage login() {
        return get(LoginPage.class);
    }

    /** @return the customers page of this scenario (the Customer window once WindowNavigator opened it) */
    public CustomersPage customers() {
        return get(CustomersPage.class);
    }

    /**
     * The Price window. Open it first with {@code windows.switchToWindow(button, "Price Stack")}
     * ({@link WindowNavigator}); before that this returns a PricePage on the scenario's first window, where its
     * elements are not found.
     *
     * @return the Price window
     */
    public PricePage price() {
        return get(PricePage.class);
    }

    /** @return the KLM GUI of this scenario */
    public KlmPage klm() {
        return get(KlmPage.class);
    }

    /** @return the GSN GUI of this scenario */
    public GsnPage gsn() {
        return get(GsnPage.class);
    }

    /**
     * Any page object of this scenario, for pages that have no named method yet.
     *
     * @param type the page class, for example HomePage.class
     * @param <T>  the page type
     * @return the scenario's instance of that page, created on first use
     */
    public <T extends PageComponents> T get(Class<T> type) {
        return PageComponentsFactory.get(type, session);
    }
}
