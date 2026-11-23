@windows @grid
Feature: Data grid on three pages
  The grid steps work on the grid of the window the scenario is on, so the same steps serve the home page, the
  Price Booking window and the Customer window. Columns are named by their header text and values are written
  exactly as shown. Needs the real application (not the sample app), so it is not part of the CI suites.
  The column names and values below are examples: replace them with the real ones.

  # Replace the @PRGBATR- ids with the real Jira test case ids.

  @PRGBATR-1016
  Scenario: Home page grid - read values
    Then the grid is loaded
    And the grid column "Name" shows
      | COE |
      | NBE |
    And in the grid row where "Name" is "COE", "Role" is "Admin"

  @PRGBATR-1017
  Scenario: Price Booking grid - select rows
    When I open the Price Booking window
    Then the grid is loaded
    When I check the grid row where "Booking" is "Booking A"
    Then the grid row where "Booking" is "Booking A" is checked
    When I uncheck the grid row where "Booking" is "Booking A"
    Then the grid row where "Booking" is "Booking A" is not checked
    When I check column 1 in the grid row where "Booking" is "Booking B"

  @PRGBATR-1018
  Scenario: Customer grid - search, pick a column's checkbox, open a cell
    When I open the Price Booking window
    And I open the Customer window
    Then the grid is loaded
    And the grid column "Customer" contains "Ada Lovelace"
    And the grid column "Customer" does not contain "Nobody"
    When I check "Select" in the grid row where "Customer" is "Ada Lovelace"
    And I click "Email" in the grid row where "Customer" is "Ada Lovelace"
