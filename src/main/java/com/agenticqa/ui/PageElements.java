package com.agenticqa.ui;

import com.agenticqa.ui.LocatorRepository.Definition;
import com.agenticqa.ui.healing.Healer;
import com.agenticqa.ui.healing.SmartLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The elements of one page, looked up by name in its locator files ({@code locators/FILE.json}) when they are
 * used, so page objects hold no selectors and need no locator fields:
 * <pre>
 * PageElements el = new PageElements(page, healer, "customers", "customer-form", "pagination", "common");
 * el.get("searchInput").fill(text);       // first file that has "searchInput"
 * el.get("pagination.nextLink").click();  // only locators/pagination.json
 * </pre>
 * A page can use several files: one per area of the page (search, form, table) and shared files for parts that
 * several pages have (pagination, header). Plain names are searched in the files in the order given, and the
 * first file that has the name wins; {@code FILE.NAME} picks one file. See {@link LocatorRepository#find}.
 * <p>
 * Elements marked {@code heal} in their file are resolved through the scenario's {@link Healer} on every call
 * (so a renamed id heals); the others are plain Playwright locators. The healing key is {@code FILE.NAME}, so
 * the same name in two files never shares a fingerprint.
 */
public final class PageElements {

    private final Page page;
    private final Healer healer;
    private final List<String> files;

    /**
     * Binds locator files to the scenario's page.
     *
     * @param page   the Playwright page
     * @param healer the scenario's healer
     * @param files  the locator file names without .json, for example "customers", "pagination", in search order;
     *               a file listed twice counts once
     * @throws IllegalArgumentException if no file is given, or a file does not exist or is malformed (checked
     *                                  now, not on first use)
     */
    public PageElements(Page page, Healer healer, String... files) {
        Set<String> unique = new LinkedHashSet<>(List.of(files));
        if (unique.isEmpty()) throw new IllegalArgumentException("A page needs at least one locator file");
        this.page = page;
        this.healer = healer;
        this.files = List.copyOf(unique);
        this.files.forEach(LocatorRepository::page);
    }

    /**
     * The locator files of this page.
     *
     * @return the file names, in search order
     */
    public List<String> files() {
        return files;
    }

    /**
     * Finds an element now.
     *
     * @param name the locator name, or FILE.NAME
     * @return the locator, healed if needed
     * @throws IllegalArgumentException if no file of this page has that name
     */
    public Locator get(String name) {
        Definition definition = LocatorRepository.find(files, name);
        if (!definition.heal()) return page.locator(definition.selector());
        return new SmartLocator(page, healer, definition.key(), definition.description(), definition.selector())
                .locator();
    }

    /**
     * Finds an element inside another one, for example a cell inside a table row.
     *
     * @param parent the outer element
     * @param name   the locator name of the inner element, or FILE.NAME
     * @return the inner locator (never healed: inner selectors are relative)
     */
    public Locator within(Locator parent, String name) {
        return parent.locator(LocatorRepository.find(files, name).selector());
    }
}
