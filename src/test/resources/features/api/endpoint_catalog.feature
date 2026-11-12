@api
Feature: Endpoints called by name from the endpoint catalog
  Endpoints are declared once in src/main/resources/endpoints/<service>.yaml (method, path, default body), so
  feature files and Java code call them by name and hold no URLs. Adding an endpoint is one line in that file:
  no request class, record or client method per endpoint.

  Scenario: Create, read, update and delete a customer by endpoint name
    Given a new "ada" request for "customers.create"
    And the "ada" request fields are set:
      | name   | Ada Lovelace {unique}              |
      | email  | ada.lovelace.{unique}@example.test |
      | status | inactive                           |
    When I call "customers.create" with the "ada" request
    Then the "ada" response status is 201
    And I save the "ada" response field "id" as "adaId"
    And "/customers/${adaId}" on the "customers" service is deleted after the scenario

    When I call "customers.get" as "adaRead" with:
      | id | ${adaId} |
    Then the "adaRead" response fields are:
      | id     | ${adaId}              |
      | name   | Ada Lovelace {unique} |
      | status | inactive              |

    Given a new "adaUpdate" request from the "adaRead" response
    And the "adaUpdate" request field "id" is removed
    And the "adaUpdate" request field "status" is set to "active"
    And the "adaUpdate" request path parameter "id" is "${adaId}"
    When I call "customers.update" with the "adaUpdate" request
    Then the "adaUpdate" response status is 200
    And the "adaUpdate" response field "status" is "active"

    When I call "customers.delete" as "adaDelete" with:
      | id | ${adaId} |
    Then the "adaDelete" response status is 204

  Scenario: A path parameter falls back to the variable of the same name
    Given a new "grace" request for "customers.create"
    And the "grace" request field "name" is set to "Grace Hopper {unique}"
    When I call "customers.create" with the "grace" request
    Then the "grace" response status is 201
    And I save the "grace" response field "id" as "id"
    And "/customers/${id}" on the "customers" service is deleted after the scenario
    When I call "customers.get" as "graceRead"
    Then the "graceRead" response field "name" is "Grace Hopper {unique}"

  Scenario: A list endpoint without parameters
    When I call "customers.list" as "all"
    Then the "all" response status is 200
