@ui @e2e
Feature: Customer request built in the feature file, checked in the browser
  The feature file writes the API request itself: one step starts it, later steps change it, another step sends
  it, and browser steps then check what the page shows. All steps share data through the scenario's TestSession:
    - the request draft (Keys.REQUEST) is built and edited by the request steps,
    - sending it stores the response (Keys.RESPONSE) and the created or updated customer (Keys.CUSTOMER),
    - the browser steps search for and compare against that stored customer.
  {unique} is replaced by one random token per scenario, so parallel runs never see each other's data.

  Scenario: Create a customer from a request table, change the request, update it, and verify each step on the page
    # API request built in the feature file
    Given a new customer request:
      | name  | Mary Jackson {unique}              |
      | email | mary.jackson.{unique}@example.test |
    # the next step changes the request before it is sent
    And the request field "status" is set to "inactive"
    When I send the create customer request
    Then the API responds with status 201
    And the customer can be found by id with status "inactive"
    # browser checks the customer the request created
    When I search the customers page for that customer
    Then the results show only that customer with status "inactive"
    # change the same request again (the email stays as it was) and send it as an update
    Given the request fields are updated:
      | name   | Mary W. Jackson {unique} |
      | status | active                   |
    When I send the update request for the customer
    Then the API responds with status 200
    # browser checks the updated customer: new name and status, same id and email
    When I search the customers page for that customer
    Then the results show only that customer with status "active"
