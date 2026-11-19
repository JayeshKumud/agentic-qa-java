package com.agenticqa.ui;

import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

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

    /** Opens the GSN GUI and waits until it has loaded. */
    public GsnPage open() {
        page.navigate(Settings.getGsnUrl());
        page.waitForLoadState();
        return this;
    }
}
