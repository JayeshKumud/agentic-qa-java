@ui @e2e
Feature: Customer journey across the page and the API
  One customer moves back and forth between the browser and the REST API. Every step works on "the customer"
  that the previous steps stored in the scenario's TestSession (key Keys.CUSTOMER), so no step needs ids,
  names or emails written in the feature file:
    - the UI step that adds the customer reads its id from the results table and stores it,
    - the API steps use that id, and store the customer the API returns (with its new status),
    - the UI search steps read the stored name, and the checks compare the page with the stored customer.

  Scenario: A customer added on the page is updated and deleted through the API
    # UI -> stores the customer (id read from the page)
    When I add a customer named "Katherine Johnson" through the form
    # API <- uses the stored id
    Then the customer can be found by id with status "active"
    # API -> stores the updated customer
    When I change the customer status to "inactive"
    # UI <- searches for the stored name and compares the row with the stored customer
    And I search the customers page for that customer
    Then the results show only that customer with status "inactive"
    # API <- deletes by the stored id
    When I delete the customer
    # UI <- the page no longer shows it
    And I search the customers page for that customer
    Then the page says no customers were found
