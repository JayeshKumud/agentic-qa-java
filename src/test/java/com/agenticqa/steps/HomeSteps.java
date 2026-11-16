package com.agenticqa.steps;

import com.agenticqa.ui.HomePage;
import com.agenticqa.ui.PageComponentFactory;
import io.cucumber.java.en.When;

/** Steps for the home page. */
public class HomeSteps {

    /**
     * Selects a role in the change-role dialog: {@code When I select the "COE" role}.
     *
     * @param role the role as shown in the Role column
     */
    @When("I select the {string} role")
    public void selectRole(String role) {
        homePage().selectRole(role);
    }

    /**
     * The home page of this scenario, from the factory (created on first use, then reused).
     *
     * @return the home page
     */
    private HomePage homePage() {
        return PageComponentFactory.get(HomePage.class);
    }
}
