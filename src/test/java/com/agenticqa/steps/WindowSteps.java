package com.agenticqa.steps;

import com.agenticqa.ui.Pages;
import com.agenticqa.ui.Window;
import com.agenticqa.ui.WindowNavigator;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

/**
 * Steps that move between the app's windows ({@link WindowNavigator#switchToWindow(Window)}). After a switch, the
 * steps of that window act on it, for example {@code pages.price()} for the Price window. The fixed steps ("I open
 * the Price Stack window") pass a {@link Window}; "I switch to the ... window" takes the window's text from the
 * feature and looks it up with {@link Window#of}.
 */
public class WindowSteps {

    private final WindowNavigator windows;
    private final Pages pages;

    /**
     * Receives the scenario's window navigator and page objects from PicoContainer.
     *
     * @param windows moves between the windows
     * @param pages   the scenario's page objects
     */
    public WindowSteps(WindowNavigator windows, Pages pages) {
        this.windows = windows;
        this.pages = pages;
    }

    /**
     * Switches to a window, opening it from the current window's dropdown if it is not open yet:
     * {@code When I switch to the "Price Stack" window}, {@code When I switch to the "Home" window}.
     *
     * @param window the option's text, exactly as shown, which is also the window's title; or "Home"
     */
    @When("I switch to the {string} window")
    public void switchToWindow(String window) {
        windows.switchToWindow(Window.of(window));
    }

    /**
     * Same as "I switch to the ... window", worded for going back to a window that is already open:
     * {@code When I go back to the "Price Stack" window}.
     *
     * @param window the option text the window was opened with, or "Home"
     */
    @When("I go back to the {string} window")
    public void backToWindow(String window) {
        windows.switchToWindow(Window.of(window));
    }

    /** Opens the Price Stack window from the current window's dropdown: {@code When I open the Price Stack window}. */
    @When("I open the Price Stack window")
    public void openPriceStack() {
        windows.switchToWindow(Window.PRICE_STACK);
    }

    /**
     * Opens the Customer window from the current window's dropdown: {@code When I open the Customer window}. The
     * Customer Window option is in the Price Stack window's dropdown, so open that first.
     */
    @When("I open the Customer window")
    public void openCustomer() {
        windows.switchToWindow(Window.CUSTOMER_WINDOW);
    }

    /**
     * Selects bookings and days on the Price window ({@link com.agenticqa.ui.PricePage#applySearchs}), from a table
     * with one row per field and its values across:
     * <pre>
     * When I apply the search on the Price window
     *   | bookings | Booking A | Booking C |
     *   | days     | Monday    | Friday    |
     * </pre>
     * Cucumber turns the first column into the key and the rest of the row into a list. Empty cells (rows of
     * different length) are skipped, and a missing row means nothing is selected for it.
     *
     * @param table the values per field, "bookings" and "days"
     */
    @When("I apply the search on the Price window")
    public void applySearch(Map<String, List<String>> table) {
        pages.price().applySearchs(values(table, "bookings"), values(table, "days"));
    }

    /** The non-empty values of one row of the table, or an empty list if the row is missing. */
    private static List<String> values(Map<String, List<String>> table, String field) {
        return table.getOrDefault(field, List.of()).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /** Clicks the filter button of the Price window: {@code When I open the filter on the Price window}. */
    @When("I open the filter on the Price window")
    public void openFilter() {
        pages.price().openFilter();
    }
}
