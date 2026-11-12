package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of every page object. It holds the Playwright page and the page's locator files, and offers the
 * actions every page needs, by locator name, so a page object holds only business actions:
 * <pre>
 * public class OrdersPage extends BasePage&lt;OrdersPage&gt; {
 *     private static final String[] LOCATORS = {"orders", "order-form", "pagination"};
 *
 *     public OrdersPage(Page page, Healer healer) {
 *         super(page, healer, LOCATORS);          // + common.json, added here
 *     }
 *
 *     &#64;Override
 *     protected String path() {
 *         return "/orders";
 *     }
 *
 *     public OrdersPage addOrder(String sku) {
 *         fill("skuInput", sku);
 *         return submit("addButton");             // click and wait for the next page
 *     }
 * }
 * </pre>
 * <ul>
 *   <li><b>Several locator files.</b> The subclass names its files; {@value #COMMON}.json (elements every page
 *       has, such as the heading) is always added last. A plain name is looked up in that order and the first
 *       file with it wins, so a page file can override a common entry; {@code "pagination.nextLink"} picks one
 *       file. See {@link PageElements}.</li>
 *   <li><b>Fluent.</b> The type parameter is the page class itself, so actions return the concrete page and
 *       calls chain: {@code page.open().search("Ada").nextPage()}.</li>
 *   <li><b>Waits.</b> Actions use Playwright's auto-waiting with the configured wait times ({@code ui.waits}
 *       in config/common.yaml). {@link #submit} also waits for the page that the click loads, so the next read
 *       never sees the old document.</li>
 * </ul>
 *
 * @param <T> the page class itself
 */
public abstract class BasePage<T extends BasePage<T>> {

    /** Locator file every page uses, locators/common.json. */
    public static final String COMMON = "common";

    /** The scenario's browser page. */
    protected final Page page;

    /** The elements of this page, from its locator files. */
    protected final PageElements el;

    /**
     * Binds the page object to the scenario's page and its locator files.
     *
     * @param page         the Playwright page (its context has the UI base URL)
     * @param healer       this scenario's healer
     * @param locatorFiles this page's locator files without .json, most specific first; common is added last
     * @throws IllegalArgumentException if a locator file does not exist or is malformed
     */
    protected BasePage(Page page, Healer healer, String... locatorFiles) {
        List<String> files = new ArrayList<>(List.of(locatorFiles));
        files.add(COMMON);
        this.page = page;
        this.el = new PageElements(page, healer, files.toArray(String[]::new));
    }

    /**
     * Path of the page, relative to ui.baseUrl.
     *
     * @return for example "/" or "/orders"
     */
    protected abstract String path();

    /**
     * This page, typed as the subclass, for fluent actions.
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    // ------------------------------------------------------------------------------------------- navigation

    /**
     * Opens the page at {@link #path()}.
     *
     * @return this page
     */
    public T open() {
        page.navigate(path());
        return self();
    }

    /** @return the document title */
    public String title() {
        return page.title();
    }

    /** @return the current URL */
    public String url() {
        return page.url();
    }

    /** @return the page heading (common.json "heading") */
    public Locator heading() {
        return el.get("heading");
    }

    // ------------------------------------------------------------------------------------------- elements

    /**
     * An element of this page.
     *
     * @param name the locator name, or FILE.NAME
     * @return the locator (self-healing if marked so)
     */
    public Locator find(String name) {
        return el.get(name);
    }

    /**
     * Types into a field, replacing what it held.
     *
     * @param name the locator name
     * @param text the text
     * @return this page
     */
    protected T fill(String name, String text) {
        el.get(name).fill(text);
        return self();
    }

    /**
     * Clicks an element that does not load another page.
     *
     * @param name the locator name
     * @return this page
     */
    protected T click(String name) {
        el.get(name).click();
        return self();
    }

    /**
     * Clicks an element that loads a new page (a submit button, a link) and waits until it has loaded.
     *
     * @param name the locator name
     * @return this page, showing the new document
     */
    @SuppressWarnings("deprecation") // waitForNavigation is the simplest wait for a full page load after a click
    protected T submit(String name) {
        Locator target = el.get(name);
        page.waitForNavigation(target::click);
        return self();
    }

    /**
     * Picks an option of a select box by its value or label.
     *
     * @param name   the locator name
     * @param option the option
     * @return this page
     */
    protected T select(String name, String option) {
        el.get(name).selectOption(option);
        return self();
    }

    /**
     * The visible text of an element.
     *
     * @param name the locator name
     * @return the text, trimmed
     */
    protected String text(String name) {
        return el.get(name).innerText().trim();
    }

    /**
     * Tells whether an element is shown right now, without waiting. Meant for elements marked
     * {@code "heal": false}: a missing self-healing element starts a search for a look-alike instead.
     *
     * @param name the locator name
     * @return true if it exists and is visible
     */
    public boolean isVisible(String name) {
        return el.get(name).isVisible();
    }
}
