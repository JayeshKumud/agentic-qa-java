package com.agenticqa.steps;

import com.agenticqa.ui.Pages;
import com.agenticqa.ui.WindowNavigator;
import io.cucumber.java.en.When;

/**
 * Steps that move between the app's windows ({@link WindowNavigator#switchToWindow(String, String)}). After a switch, the
 * steps of that window act on it, for example {@code pages.price()} for the Price window.
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
     * Clicks a button in the current window, then one of its options, which opens a window, and switches to it:
     * {@code When I switch from "Add tool" to the "Price Stack" window}.
     *
     * @param button   the button's text in the current window, exactly as shown, for example "Add tool"
     * @param window   the option's text, exactly as shown, which is also the new window's title
     */
    @When("I switch from {string} to the {string} window")
    public void switchToWindow(String button, String window) {
        windows.switchToWindow(button, window);
    }

    /**
     * Switches back to a window opened earlier in this scenario: {@code When I go back to the "Price Stack" window},
     * or to the first window with {@code When I go back to the "Home" window}.
     *
     * @param window the option text the window was opened with, or "Home"
     */
    @When("I go back to the {string} window")
    public void backToWindow(String window) {
        windows.switchToWindow(window);
    }

    /** Clicks the filter button of the Price window: {@code When I open the filter on the Price window}. */
    @When("I open the filter on the Price window")
    public void openFilter() {
        pages.price().openFilter();
    }
}
