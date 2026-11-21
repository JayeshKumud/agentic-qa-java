package com.agenticqa.steps;

import com.agenticqa.ui.CommonActions;
import com.agenticqa.ui.PageElements;
import com.agenticqa.ui.WindowNavigator;
import io.cucumber.java.en.When;

/**
 * Steps for the buttons every page has ({@link CommonActions}). They act on the window the scenario is on: the
 * home page, or the window the last {@code I switch from ... to the ... window} step opened.
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

    /** Clicks the keyboard shortcuts button of the current window: {@code When I open the keyboard shortcuts}. */
    @When("I open the keyboard shortcuts")
    public void openKeyboardShortcuts() {
        PageElements current = windows.current().elements();
        CommonActions.openKeyboardShortcuts(current.page(), current.healer());
    }
}
