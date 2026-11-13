@api
Feature: Customer requests built as POJOs
  The request body is a Java object (com.agenticqa.api.model.CustomerRequest) instead of a JSON template.
  EndpointClient serializes it with Jackson; null fields are not sent.

  Scenario: Create a customer from a request POJO
    Given a customer request object:
      | name  | Grace Hopper {unique}              |
      | email | grace.hopper.{unique}@example.test |
    And the customer request object status is "inactive"
    When I call "customers.create" with the customer request object as "pojo"
    Then the "pojo" response status is 201
    And the "pojo" response matches the customer request object
    And I save the "pojo" response field "id" as "id"
    And "/customers/${id}" on the "customers" service is deleted after the scenario

  Scenario: Update a customer with a changed request POJO
    Given a customer request object:
      | name  | Alan Turing {unique}              |
      | email | alan.turing.{unique}@example.test |
    When I call "customers.create" with the customer request object as "created"
    Then the "created" response status is 201
    And I save the "created" response field "id" as "id"
    And "/customers/${id}" on the "customers" service is deleted after the scenario
    Given the customer request object status is "inactive"
    When I call "customers.update" with the customer request object as "updated"
    Then the "updated" response status is 200
    And the "updated" response field "status" is "inactive"
    And the "updated" response matches the customer request object
