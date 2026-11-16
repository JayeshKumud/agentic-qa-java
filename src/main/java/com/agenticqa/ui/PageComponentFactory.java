package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

import java.lang.reflect.InvocationTargetException;

/**
 * Creates page objects for the running scenario and hands out the same instance on every call, from anywhere:
 * <pre>
 * private HomePage homePage() {
 *     return PageComponentFactory.get(HomePage.class);
 * }
 * </pre>
 * A page is created on first use, through its {@code (Page, Healer)} constructor, with the scenario's browser
 * page and healer. API-only scenarios that never ask for a page therefore never start a browser.
 * <p>
 * The factory keeps no state: it finds the scenario with {@link TestSession#current()}, and the created pages live
 * in that session, so they are dropped when the session closes. Parallel scenarios never share pages, and the
 * next scenario on the same thread never gets a page of a closed browser.
 */
public final class PageComponentFactory {

    private PageComponentFactory() {}

    /**
     * The page object of a type, created on first use in this scenario.
     *
     * @param pageToCreate the page class, for example HomePage.class
     * @param <T>          the page type
     * @return the scenario's instance of that page
     * @throws IllegalStateException if no scenario is running on this thread, or the page cannot be created
     */
    public static <T extends PageComponent> T get(Class<T> pageToCreate) {
        TestSession session = TestSession.current();
        return session.pageComponent(pageToCreate, () -> create(pageToCreate, session));
    }

    /**
     * Calls the page's {@code (Page, Healer)} constructor with the session's page and healer.
     *
     * @param pageToCreate the page class
     * @param session      the scenario's session
     * @param <T>          the page type
     * @return the new page
     */
    private static <T extends PageComponent> T create(Class<T> pageToCreate, TestSession session) {
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
