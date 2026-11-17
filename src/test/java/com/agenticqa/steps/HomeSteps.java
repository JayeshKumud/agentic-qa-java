package com.agenticqa.steps;

import com.agenticqa.ui.Pages;
import io.cucumber.java.en.When;

/** Steps for the home page. */
public class HomeSteps {

    private final Pages pages;

    /**
     * Receives the scenario's page objects from PicoContainer.
     *
     * @param pages the scenario's page objects, each created on first use
     */
    public HomeSteps(Pages pages) {
        this.pages = pages;
    }

    /**
     * Selects a role in the change-role dialog: {@code When I select the "COE" role}.
     *
     * @param role the role as shown in the Role column
     */
    @When("I select the {string} role")
    public void selectRole(String role) {
        pages.home().selectRole(role);
    }
}
