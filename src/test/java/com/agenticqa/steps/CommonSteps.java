package com.agenticqa.steps;

import com.agenticqa.ui.CommonActions;
import com.agenticqa.ui.HomePage;
import com.agenticqa.ui.PageElements;
import com.agenticqa.ui.Window;
import com.agenticqa.ui.WindowNavigator;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Steps for the shared buttons ({@link CommonActions}). Help and keyboard shortcuts act on the window the scenario
 * is on: the home page, or the window the last {@code I switch from ... to the ... window} step opened. Log out is
 * only on the home page, so that step switches to the home window first.
 */
public class CommonSteps {

    private final WindowNavigator windows;

    /**
     * Receives the scenario's window navigator from PicoContainer.
     *
     * @param windows knows which window the scenario is on
     */
    public CommonSteps(WindowNavigator windows) {
        this.windows = windows;
    }

    /**
     * Clicks the help button of the current window and waits until the help page it opens has loaded:
     * {@code When I open help}. The scenario stays on the current window.
     */
    @When("I open help")
    public void openHelp() {
        PageElements current = windows.current().elements();
        CommonActions.openHelp(current.page(), current.healer());
    }

    /**
     * Switches to the home window, the only one with the log out button, logs out and checks that the login page
     * is shown there (its titles and sign in button): {@code When I log out}.
     */
    @When("I log out")
    public void logout() {
        HomePage home = windows.switchToWindow(Window.HOME);
        CommonActions.logout(home);
    }

    /** Clicks the keyboard shortcuts button of the current window: {@code When I open the keyboard shortcuts}. */
    @When("I open the keyboard shortcuts")
    public void openKeyboardShortcuts() {
        PageElements current = windows.current().elements();
        CommonActions.openKeyboardShortcuts(current.page(), current.healer());
    }

    /**
     * Takes a screenshot of the visible part of the current window and attaches it to the reports under that name:
     * {@code Then I take a screenshot "after filter"}.
     *
     * @param name the screenshot's name
     */
    @Then("I take a screenshot {string}")
    public void takeScreenshot(String name) {
        windows.current().screenshot(name);
    }

    /**
     * Takes a screenshot of the whole page of the current window, scrolled parts included:
     * {@code Then I take a full page screenshot "price grid"}.
     *
     * @param name the screenshot's name
     */
    @Then("I take a full page screenshot {string}")
    public void takeFullPageScreenshot(String name) {
        windows.current().fullPageScreenshot(name);
    }
}
