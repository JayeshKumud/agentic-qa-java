@api
Feature: Customer request payloads built in the feature file
  A request body can start from a JSON file under src/test/resources/testdata, from a table, or from a raw JSON
  doc string. Any number of steps can then set or remove fields before it is sent.
  {unique} is replaced by one random token per scenario.

  Scenario: Create a customer from a JSON test data file and change it before sending
    Given a new customer request from "customers/new_customer.json"
    And the request field "status" is set to "inactive"
    And the request field "name" is set to "Katherine G. Johnson {unique}"
    When I send the create customer request
    Then the API responds with status 201
    And the customer can be found by id with status "inactive"

  Scenario: A field removed from the request falls back to the server default
    Given a new customer request from "customers/inactive_customer.json"
    And the request field "status" is removed
    When I send the create customer request
    Then the API responds with status 201
    And the customer can be found by id with status "active"

  Scenario: A missing required field is rejected
    Given a new customer request from "customers/new_customer.json"
    And the request field "email" is removed
    When I send the create customer request
    Then the API responds with status 422

  Scenario: Fields with the wrong types are rejected
    Given a new customer request from "customers/wrong_types.json"
    When I send the create customer request
    Then the API responds with status 422

  Scenario: Unknown fields are ignored by the API
    Given a new customer request from "customers/extra_fields.json"
    When I send the create customer request
    Then the API responds with status 201
    And the customer can be found by id with status "active"

  Scenario: A raw JSON body is sent exactly as written
    Given the customer request body is:
      """
      {
        "name": "Mary Jackson {unique}",
        "email": "mary.jackson.{unique}@example.test",
        "status": "inactive"
      }
      """
    When I send the create customer request
    Then the API responds with status 201
    And the customer can be found by id with status "inactive"

  Scenario: A malformed raw JSON body is rejected
    Given the customer request body is:
      """
      { "name": "Broken {unique}", "email":
      """
    When I send the create customer request
    Then the API responds with status 422

  Scenario: An update sent without a status resets it to the default
    Given a new customer request from "customers/inactive_customer.json"
    When I send the create customer request
    Then the API responds with status 201
    Given the request field "status" is removed
    When I send the update request for the customer
    Then the API responds with status 200
    And the customer can be found by id with status "active"
