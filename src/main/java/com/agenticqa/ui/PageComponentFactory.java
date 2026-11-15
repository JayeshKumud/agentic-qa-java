package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates page objects for the running scenario and hands out the same instance on every call:
 * <pre>
 * private HomePage homePage() {
 *     return PageComponentFactory.get(HomePage.class);
 * }
 * </pre>
 * A page is created on first use, through its {@code (Page, Healer)} constructor, with the scenario's browser
 * page and healer. API-only scenarios that never ask for a page therefore never start a browser.
 * <p>
 * State is kept per thread: a scenario runs on one thread from its first hook to its last, so parallel
 * scenarios never share pages. {@code Hooks} calls {@link #start} before and {@link #reset} after every
 * scenario, so the next scenario on the same thread never gets a page of a closed browser.
 */
public final class PageComponentFactory {

    private static final ThreadLocal<TestSession> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<Map<Class<?>, PageComponent>> CREATED_PAGES = ThreadLocal.withInitial(HashMap::new);

    private PageComponentFactory() {}

    /**
     * Binds the factory to the scenario that starts on this thread.
     *
     * @param session the scenario's session
     */
    public static void start(TestSession session) {
        SESSION.set(session);
        CREATED_PAGES.get().clear();
    }

    /** Forgets the scenario and its pages on this thread. */
    public static void reset() {
        CREATED_PAGES.remove();
        SESSION.remove();
    }

    /**
     * The page object of a type, created on first use in this scenario.
     *
     * @param pageToCreate the page class, for example HomePage.class
     * @param <T>          the page type
     * @return the scenario's instance of that page
     * @throws IllegalStateException if no scenario is running on this thread, or the page cannot be created
     */
    public static <T extends PageComponent> T get(Class<T> pageToCreate) {
        PageComponent existing = CREATED_PAGES.get().get(pageToCreate);
        if (existing != null) return pageToCreate.cast(existing);

        TestSession session = SESSION.get();
        if (session == null) {
            throw new IllegalStateException("No scenario is running on this thread, so " + pageToCreate.getSimpleName()
                    + " cannot be created. Pages are only available inside steps (Hooks calls PageComponentFactory.start).");
        }
        T page;
        try {
            page = pageToCreate.getConstructor(Page.class, Healer.class).newInstance(session.page(), session.healer());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(pageToCreate.getSimpleName()
                    + " needs a public constructor (Page page, Healer healer)", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("The constructor of " + pageToCreate.getSimpleName() + " failed: "
                    + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create " + pageToCreate.getSimpleName() + ": " + e, e);
        }
        CREATED_PAGES.get().put(pageToCreate, page);
        return page;
    }
}
