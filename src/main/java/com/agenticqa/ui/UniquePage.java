package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

/**
 * The Unique window: option "Unique Window" of the Price Booking window's dropdown. The app loads it into the Price
 * Booking window itself (no new browser window); {@link WindowNavigator} handles that. Open it with
 * {@code windows.switchToWindow(Window.UNIQUE_WINDOW)}, then use {@code pages.unique()}.
 * <p>
 * Its selectors live in {@code src/main/resources/locators/uniquepage.json}.
 */
public class UniquePage implements PageComponents {

    /** Name of the locator file, locators/uniquepage.json. */
    public static final String LOCATORS = "uniquepage";

    /** The window's title, which is also its dropdown option. */
    public static final String PAGE_TITLE = Window.UNIQUE_WINDOW.text();

    private final Page page;
    private final PageElements el;

    /**
     * Wraps the Unique window.
     *
     * @param page   the window the Unique page is shown in
     * @param healer this scenario's healer
     */
    public UniquePage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /** @return the title of the browser page */
    @Override
    public String getTitle() {
        return page.title();
    }

    /** @return this page's elements, used by the shared actions of {@link PageComponents} */
    @Override
    public PageElements elements() {
        return el;
    }

    /**
     * Waits until the Unique window is shown: checks its title. Add a check of an element only this window has
     * once it is in uniquepage.json.
     *
     * @return this page
     */
    @Override
    public UniquePage verifyDisplayed() {
        CommonActions.assertTitle(page, PAGE_TITLE);
        return this;
    }
}
