package com.agenticqa.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.nio.file.Path;

/**
 * A page object that {@link PageComponentsFactory} can create. Implementations need a public constructor
 * {@code (Page page, Healer healer)}, which the factory calls.
 * <p>
 * Actions that are the same on every page, such as {@link #selectDropdownOption}, are written once here as
 * default methods (the help and keyboard shortcuts buttons are in {@link CommonActions}). They use {@link #elements()}, so each runs on the page object's own window, and their locators live
 * once in {@code locators/common.json}.
 */
public interface PageComponents {

    /** @return the title of the browser page */
    String getTitle();

    /**
     * The page object's elements, bound to its own window, so the shared actions below search that window.
     *
     * @return the page object's {@link PageElements}
     */
    PageElements elements();

    /**
     * The browser window (Playwright page) this page object works on, for anything it does not offer:
     * {@code Page window = pages.price().window();}.
     *
     * @return this page object's window
     */
    default Page window() {
        return elements().page();
    }

    /**
     * Waits until this page is shown and ready, and fails clearly if it is not. Each page object says what that
     * means for it (a title, a button only that page has, ...); the default checks nothing. It is called at the
     * points where a page changes: {@link WindowNavigator} calls it for every window it opens, LoginPage after
     * log out and before sign in, HomePage after sign in.
     *
     * @return this page
     * @throws AssertionError if the page is not shown within ui.waits.assertionMs
     */
    default PageComponents verifyDisplayed() {
        return this;
    }

    /**
     * The shared elements (locators/common.json) in this page object's window:
     * {@code pages.home().shared().textButton("Log Out")}.
     *
     * @return the shared elements of this page's window
     */
    default SharedElements shared() {
        return new SharedElements(window(), elements().healer());
    }

    /**
     * The data grid in this page object's window ({@link GridLibrary}): {@code pricePage.grid().waitUntilLoaded()}.
     *
     * @return the grid of this page's window
     */
    default GridLibrary grid() {
        return new GridLibrary(window(), elements().healer());
    }

    /**
     * Takes a screenshot of the visible part of this page object's window and attaches it to the reports under the
     * step that took it ({@link Screenshots}): {@code pricePage.screenshot("after filter")}.
     *
     * @param name the name shown in the reports and used in the file name
     * @return the saved PNG file, under target/artifacts/screenshots
     */
    default Path screenshot(String name) {
        return Screenshots.take(window(), name);
    }

    /**
     * Takes a screenshot of the whole page of this page object's window, the parts scrolled out of view included:
     * {@code pricePage.fullPageScreenshot("price grid")}.
     *
     * @param name the name shown in the reports and used in the file name
     * @return the saved PNG file
     */
    default Path fullPageScreenshot(String name) {
        return Screenshots.takeFullPage(window(), name);
    }

    /**
     * Takes a screenshot of one element only: {@code pricePage.screenshot(el.get("filterButton"), "filter")}.
     *
     * @param element the element, for example a grid row or a dialog
     * @param name    the name shown in the reports and used in the file name
     * @return the saved PNG file
     */
    default Path screenshot(Locator element, String name) {
        return Screenshots.take(element, name);
    }

    /**
     * Opens this window's dropdown and picks one of its options: clicks the dropdown button (one per window, found
     * by its data-testid), then the option (the {@code li} in the option list whose span shows {@code option}). The
     * click on the option waits for the list to appear. If the list closes again before the option is clicked, the
     * button is clicked once more and the option again. Locators: dropdownButton, dropdownOption in
     * locators/common.json.
     *
     * @param option the text of the option, exactly as shown, for example {@code Window.PRICE_BOOKING.text()}
     */
    default void selectDropdownOption(String option) {
        try {
            shared().dropdownButton().click();
            shared().dropdownOption(option).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (PlaywrightException listClosedTooSoon) {
            shared().dropdownButton().click();
            shared().dropdownOption(option).click();
        }
    }

    /**
     * Clicks the input of a filter dropdown, found by the label above it ({@code <p class="dropdownV2-label">}),
     * which opens its options (filterDropdownInput in locators/common.json):
     * {@code pricePage.clickFilterDropdown("TEST1")}.
     *
     * @param label the dropdown's label exactly as shown, for example "TEST1" or "TEST2"
     */
    default void clickFilterDropdown(String label) {
        shared().filterDropdownInput(label).click();
    }

    /**
     * The text the filter dropdown's input shows, for example "chose one or more options" before anything is
     * chosen.
     *
     * @param label the dropdown's label exactly as shown
     * @return the input's value
     */
    default String filterDropdownValue(String label) {
        return shared().filterDropdownInput(label).inputValue();
    }
}
