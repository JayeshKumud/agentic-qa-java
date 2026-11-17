@api @Parallel
Feature: A zone id set in one step and read in the next steps
  "Given a unique zone id is set" creates a number no other scenario gets, even when scenarios run in parallel.
  Later steps of the same scenario read the same zone id: Java steps from the session (Keys.ZONE_ID), feature
  files as ${zoneId}.

  @PRGBATR-1006
  Scenario: Set a unique zone id and read the same zone id in the next step
    Given a unique zone id is set
    When I read the zone id
    Then the zone id read is the one that was set

  # @Sequential wins over the feature's @Parallel: this scenario runs alone, after the parallel ones.
  @Sequential @PRGBATR-1007
  Scenario: A second zone id in the same scenario replaces the first
    Given a unique zone id is set
    And I save the zone id as "firstZone"
    And a unique zone id is set
    When I read the zone id
    Then the zone id read is the one that was set
    And the zone id is not "${firstZone}"
