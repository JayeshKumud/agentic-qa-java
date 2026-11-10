@ui @e2e
Feature: Many requests, a large payload, several services and the browser in one scenario
  Requests have names, so several can be built side by side and changed over many steps before they are sent.
  Values saved from one response (${hedyId}) drive later requests and URLs, and the bridge steps hand the API's
  customers to the browser steps and back. Each request names the service it goes to; services are configured by
  name under 'services:' in the environment file, so a second microservice is just another name there.

  Scenario: Two customers from a large payload and a table, chained API calls, and checks on the page
    # request 1: a large nested payload from a file, changed over several steps
    Given a new "hedy" request from "customers/customer_profile.json"
    And the "hedy" request field "profile.addresses[0].city" is set to "Graz"
    And the "hedy" request field "profile.tags[]" is set to "beta-tester"
    And the "hedy" request field "orderHistory[24].lines[2].quantity" is set to "json:9"
    And the "hedy" request field "profile.contacts[1]" is removed
    And the "hedy" request is merged with:
      """
      {
        "status": "inactive",
        "profile": { "preferences": { "newsletter": false, "limits": { "creditEur": 7500 } } }
      }
      """
    And the "hedy" request header "X-Correlation-Id" is "journey-{unique}"
    Then the "hedy" request field "profile.addresses[0].city" is "Graz"
    And the "hedy" request field "profile.preferences.limits" is "json:{\"creditEur\":7500,\"discountPercent\":5}"
    And the "hedy" request field "profile.tags" is "json:[\"inventor\",\"vip\",\"beta-tester\"]"
    And the "hedy" request field "orderHistory[-1].lines[2].quantity" is "9"

    # request 2: built from a table, side by side with request 1
    Given a new "grace" request:
      | name            | Grace Hopper {unique}              |
      | email           | grace.hopper.{unique}@example.test |
      | profile.rank    | Rear Admiral                       |
      | profile.ships[] | USS Hopper                         |

    # send both, save the ids, and register cleanup on the service
    When I send the "hedy" request as POST to "/customers" on the "customers" service
    Then the "hedy" response status is 201
    And the "hedy" response fields are:
      | name   | Hedy Lamarr {unique}              |
      | email  | hedy.lamarr.{unique}@example.test |
      | status | inactive                          |
    And I save the "hedy" response field "id" as "hedyId"
    And "/customers/${hedyId}" on the "customers" service is deleted after the scenario
    When I send the "grace" request as POST to "/customers" on the "customers" service
    Then the "grace" response status is 201
    And I save the "grace" response field "id" as "graceId"
    And "/customers/${graceId}" on the "customers" service is deleted after the scenario

    # chained calls: read back by the saved id, turn the response into an update request
    When I send GET "/customers/${hedyId}" to the "customers" service as "hedyRead"
    Then the "hedyRead" response field "id" is "${hedyId}"
    Given a new "hedyUpdate" request from the "hedyRead" response
    And the "hedyUpdate" request field "id" is removed
    And the "hedyUpdate" request field "status" is set to "active"
    When I send the "hedyUpdate" request as PUT to "/customers/${hedyId}" on the "customers" service
    Then the "hedyUpdate" response status is 200
    And the "hedyUpdate" response field "status" is "active"

    # API -> GUI: the page shows both customers as the API left them
    Given the "hedyUpdate" response is the customer
    When I search the customers page for that customer
    Then the results show only that customer with status "active"
    Given the "grace" response is the customer
    When I search the customers page for that customer
    Then the results show only that customer with status "active"

    # GUI -> API: a customer added on the page is deleted with the generic steps
    When I add a customer named "Radia Perlman" through the form
    And I save the customer's id as "radiaId"
    And I send DELETE "/customers/${radiaId}" to the "customers" service as "radiaDelete"
    Then the "radiaDelete" response status is 204
    When I send GET "/customers/${radiaId}" to the "customers" service as "radiaGone"
    Then the "radiaGone" response status is 404
    When I search the customers page for that customer
    Then the page says no customers were found
