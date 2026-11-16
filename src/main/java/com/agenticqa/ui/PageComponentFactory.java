package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates the page objects of one scenario and hands out the same instance on every call. Step classes receive it
 * from PicoContainer, like {@link TestSession}:
 * <pre>
 * public HomeSteps(PageComponentFactory pages) {
 *     this.pages = pages;
 * }
 *
 * private HomePage homePage() {
 *     return pages.get(HomePage.class);
 * }
 * </pre>
 * A page is created on first use, through its {@code (Page, Healer)} constructor, with the scenario's browser
 * page and healer. API-only scenarios that never ask for a page therefore never start a browser.
 * <p>
 * PicoContainer creates one factory per scenario and gives every step class of that scenario the same one, then
 * discards it. Parallel scenarios therefore never share pages, and the next scenario never gets a page of a
 * closed browser, without any ThreadLocal or cleanup in the hooks.
 */
public class PageComponentFactory {

    private final TestSession session;
    private final Map<Class<? extends PageComponent>, PageComponent> createdPages = new HashMap<>();

    /**
     * Receives this scenario's session from PicoContainer.
     *
     * @param session the per-scenario session
     */
    public PageComponentFactory(TestSession session) {
        this.session = session;
    }

    /**
     * The page object of a type, created on first use in this scenario.
     *
     * @param pageToCreate the page class, for example HomePage.class
     * @param <T>          the page type
     * @return the scenario's instance of that page
     * @throws IllegalStateException if the page cannot be created
     */
    public synchronized <T extends PageComponent> T get(Class<T> pageToCreate) {
        PageComponent existing = createdPages.get(pageToCreate);
        if (existing != null) return pageToCreate.cast(existing);

        T page = create(pageToCreate);
        createdPages.put(pageToCreate, page);
        return page;
    }

    /**
     * Calls the page's {@code (Page, Healer)} constructor with the session's page and healer.
     *
     * @param pageToCreate the page class
     * @param <T>          the page type
     * @return the new page
     */
    private <T extends PageComponent> T create(Class<T> pageToCreate) {
        try {
            return pageToCreate.getConstructor(Page.class, Healer.class).newInstance(session.page(), session.healer());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(pageToCreate.getSimpleName()
                    + " needs a public constructor (Page page, Healer healer)", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("The constructor of " + pageToCreate.getSimpleName() + " failed: "
                    + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create " + pageToCreate.getSimpleName() + ": " + e, e);
        }
    }
}
