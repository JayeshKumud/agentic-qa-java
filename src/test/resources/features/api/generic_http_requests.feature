@api
Feature: Generic HTTP steps for any configured service
  The same steps work for every service under 'services:' in the environment file: named requests built from
  files, tables or doc strings, edited by path, sent with any method, and checked or saved by path.

  Scenario: A request built from nothing by path, with typed values, sent and read back
    Given a new "ada" request:
      | name    | Ada Lovelace {unique}              |
      | email   | ada.lovelace.{unique}@example.test |
      | status  | inactive                           |
      | extra.n | json:3                             |
    When I send the "ada" request as POST to "/customers" on the "customers" service
    Then the "ada" response status is 201
    And the API responds with status 201
    And I save the "ada" response field "id" as "adaId"
    And "/customers/${adaId}" on the "customers" service is deleted after the scenario
    When I send GET "/customers/${adaId}" to the "customers" service as "adaRead"
    Then the "adaRead" response fields are:
      | id     | ${adaId}                           |
      | name   | Ada Lovelace {unique}              |
      | status | inactive                           |

  Scenario: A validation error can be inspected by path
    Given a new "broken" request from "customers/new_customer.json"
    And the "broken" request field "email" is removed
    When I send the "broken" request as POST to "/customers" on the "customers" service
    Then the "broken" response status is 422
    And the "broken" response field "detail" has 1 item
    And the "broken" response field "detail[0].loc" is "json:[\"body\",\"email\"]"

  Scenario: The customer steps and the generic steps share the same request and response
    Given a new customer request from "customers/inactive_customer.json"
    And the "customer" request field "status" is set to "active"
    When I send the create customer request
    Then the "customer" response status is 201
    And the "customer" response field "status" is "active"
    And the customer can be found by id with status "active"
