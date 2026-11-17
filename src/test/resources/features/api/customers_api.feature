@api
Feature: Customers REST API
  The customers endpoint supports the full create, read, update and delete cycle.

  @smoke
  Scenario: Create and find a customer
    When I create a customer named "Ada Lovelace"
    Then the API responds with status 201
    And the customer can be found by id with status "active"
    And the customer appears in the customer list

  Scenario: Update a customer's status
    Given a customer named "Grace Hopper" exists
    When I change the customer status to "inactive"
    Then the API responds with status 200
    And the customer can be found by id with status "inactive"

  Scenario: Delete a customer
    Given a customer named "Alan Turing" exists
    When I delete the customer
    Then the API responds with status 204
    And the customer no longer appears in the customer list