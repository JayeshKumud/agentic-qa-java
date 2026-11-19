package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

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

    /** Opens the KLM GUI and waits until it has loaded. */
    public KlmPage open() {
        page.navigate(Settings.getKlmUrl());
        page.waitForLoadState();
        return this;
    }
}
