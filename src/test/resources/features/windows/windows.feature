@windows
Feature: Moving between windows
  Each dropdown option opens its own window. The steps switch to it, act on it, and can go back to a window
  that is already open. Needs the real application (not the sample app), so it is not part of the CI suites.

  # Replace @PRGBATR-1015 with the real Jira test case id.
  @PRGBATR-1015
  Scenario: Move from the home page to Price and Customer
    When I switch from "Add Master" to the "Price Window" window
    And I open the filter on the Price window
    And I open help
    And I switch from "Price Window" to the "Customer Window" window
    And I open the keyboard shortcuts
    And I go back to the "Price Window" window
    And I go back to the "Home" window
