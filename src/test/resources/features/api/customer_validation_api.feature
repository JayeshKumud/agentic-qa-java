@api
Feature: API classes in the existing framework's style
  A customer is created through CustomerValidationApi (ApiFactory, REST Assured) and then checked with the
  framework's own steps, which read it as "the customer".

  Scenario: Post a customer with the validation API and find it
    When I post a customer named "Grace Hopper" with the validation API
    Then the customer can be found by id with status "active"
    And the customer appears in the customer list
