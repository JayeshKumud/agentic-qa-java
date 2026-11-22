@windows
Feature: Moving between windows
  Each dropdown option opens its own window. The steps switch to it, act on it, and can go back to a window
  that is already open. Needs the real application (not the sample app), so it is not part of the CI suites.

  # Replace @PRGBATR-1015 with the real Jira test case id.
  @PRGBATR-1015
  Scenario: Move from the home page to Price and Customer
    When I open the Price Stack window
    And I open the filter on the Price window
    And I open help
    And I open the Customer window
    And I open the keyboard shortcuts
    And I go back to the "Price Stack" window
    Then the Price window is displayed
    And I go back to the "Home" window
