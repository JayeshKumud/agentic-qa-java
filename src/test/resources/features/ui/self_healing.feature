@ui @healing
Feature: Self-healing locators
  When an element's selector stops matching, the framework finds the element by its last known fingerprint,
  keeps the test running and reports the change so the page object can be updated.

  Scenario: Search keeps working after the search box id changes
    Given a customer named "Ada Lovelace" exists
    And I search the customers page for that customer
    And no locator was healed
    And the app's HTML renames the id "search-input" to "customer-search"
    When I search the customers page for that customer
    Then the results show only that customer with status "active"
    And the "search input" locator was healed to "#customer-search"

  Scenario: Adding a customer keeps working after the button id changes
    Given I add a customer named "Alan Turing" through the form
    And the app's HTML renames the id "add-customer-button" to "create-customer"
    When I add a customer named "Grace Hopper" through the form
    Then the customer can be found by id with status "active"
    And the "add customer button" locator was healed to "#create-customer"
