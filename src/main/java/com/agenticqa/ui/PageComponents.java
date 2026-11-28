package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

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
     * It is the window's only grid, or, if the scenario chose one for this window ({@link TestSession#useGrid}, the
     * step "I use the grid with column ...", or {@link TestSession#useGridIn}, the step "I use the grid in ..."),
     * that grid. With several grids and no choice, use {@link #grid(String)} or {@link #gridIn(String)}.
     *
     * @return the grid of this page's window
     */
    default GridLibrary grid() {
        String container = TestSession.chosenGridContainer(window());
        if (container != null) return gridIn(container);
        String column = TestSession.chosenGridColumn(window());
        return column == null ? new GridLibrary(window(), elements().healer()) : grid(column);
    }

    /**
     * The grid in an element of this page, for windows with several grids that no column tells apart:
     * {@code pricePage.gridIn("bookingsGrid").waitUntilLoaded()}. The element is a locator of this page object's
     * locator file, the element around the grid ({@code "#bookings"}) or the grid element itself
     * ({@code "#bookings .ag-root-wrapper"}).
     *
     * @param containerName the locator name in this page's locator file, for example "bookingsGrid"
     * @return the grid in that element
     * @throws IllegalArgumentException if the page's locator file has no locator with that name
     */
    default GridLibrary gridIn(String containerName) {
        return new GridLibrary(window(), elements().healer(), elements().get(containerName), containerName);
    }

    /**
     * The grid of this page object's window that has a column with this header, for windows with several grids:
     * {@code pricePage.grid("Booking").waitUntilLoaded()}. Pick a column only that grid has.
     *
     * @param column the header text of one of that grid's columns, exactly as shown
     * @return that grid
     */
    default GridLibrary grid(String column) {
        return new GridLibrary(window(), elements().healer(), column);
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
