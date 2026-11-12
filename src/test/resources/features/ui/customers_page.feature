@ui
Feature: Customers page
  Users can find customers by name, page through results and add new customers.

  @smoke
  Scenario: A customer created through the API appears on the page
    Given a customer named "Ada Lovelace" exists
    When I search the customers page for that customer
    Then the results show only that customer with status "active"

  Scenario: Add a customer through the form
    When I add a customer named "Grace Hopper" through the form
    Then the customer can be found by id with status "active"
    And the customer appears in the customer list

  Scenario: Search shows a message when nothing matches
    When I search the customers page for a name that matches nobody
    Then the page says no customers were found
    And the page heading is "Customers"

  Scenario: Results are shown five per page
    Given 6 customers share a unique name tag
    When I search the customers page for that tag
    Then the page shows 5 customers and "Page 1 of 2"
    When I go to the next page
    Then the page shows 1 customer and "Page 2 of 2"
    When I go to the previous page
    Then the page shows 5 customers and "Page 1 of 2"