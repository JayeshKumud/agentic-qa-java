package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Page object of the GSN GUI, a separate URL ({@code gui.gsn.url} in the properties files).
 * Selectors live in {@code src/main/resources/locators/gsn.json}.
 */
public class GsnPage implements PageComponent {

    /** Name of the locator file, locators/gsn.json. */
    public static final String LOCATORS = "gsn";

    private final Page page;
    private final PageElements el;

    public GsnPage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    @Override
    public String getTitle() {
        return page.title();
    }

    /** @return this page's elements, used by the shared actions of {@link PageComponent} such as selectDropdownOption */
    @Override
    public PageElements elements() {
        return el;
    }

    /**
     * Opens the GSN GUI: waits for DOMContentLoaded (gui.openTimeoutMs), then until the login input is
     * visible (gui.loginInputTimeoutMs).
     */
    public GsnPage open() {
        page.navigate(Settings.getGsnUrl(), new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(Settings.getGuiOpenTimeoutMs()));
        el.get("loginInput").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(Settings.getLoginInputTimeoutMs()));
        return this;
    }

    /** Logs in with a user name and password and waits for the next page (DOMContentLoaded). */
    public GsnPage login(String userName, String password) {
        el.get("loginInput").fill(userName);
        el.get("passwordInput").fill(password);
        el.get("loginButton").click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(Settings.getGuiOpenTimeoutMs()));
        return this;
    }
}
