package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Page object of the KLM GUI, a separate URL ({@code gui.klm.url} in the properties files).
 * Selectors live in {@code src/main/resources/locators/klm.json}.
 */
public class KlmPage implements PageComponent {

    /** Name of the locator file, locators/klm.json. */
    public static final String LOCATORS = "klm";

    private final Page page;
    private final PageElements el;

    public KlmPage(Page page, Healer healer) {
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
     * Opens the KLM GUI: waits for DOMContentLoaded (gui.openTimeoutMs), then until the login input is
     * visible (gui.loginInputTimeoutMs).
     */
    public KlmPage open() {
        page.navigate(Settings.getKlmUrl(), new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(Settings.getGuiOpenTimeoutMs()));
        el.get("loginInput").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(Settings.getLoginInputTimeoutMs()));
        return this;
    }

    /** Logs in with a user name and password and waits for the next page (DOMContentLoaded). */
    public KlmPage login(String userName, String password) {
        el.get("loginInput").fill(userName);
        el.get("passwordInput").fill(password);
        el.get("loginButton").click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(Settings.getGuiOpenTimeoutMs()));
        return this;
    }
}
