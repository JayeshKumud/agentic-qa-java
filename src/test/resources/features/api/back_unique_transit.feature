@api @Parallel
Feature: Back unique transit uses the uniques stored earlier in the scenario
  A step stores each unique by its base name in the scenario's TestSession (Keys.BASE_UNIQUE). BackUniqueTransit
  reads them with TestSession.current() and swaps each base name for its registration id. Values that are not a
  stored base name are used as written. Parallel scenarios never see each other's uniques.

  Scenario: Base names are replaced by their registration ids
    Given the unique "U1" has registration id "REG-1001"
    And the unique "U2" has registration id "REG-1002"
    When I build a back unique transit at "10:00" for "U1, U2, REG-77"
    Then the transit registration ids are "REG-1001, REG-1002, REG-77"

  Scenario: Without stored uniques the values are used as written
    When I build a back unique transit at "11:00" for "REG-5, REG-6"
    Then the transit registration ids are "REG-5, REG-6"
