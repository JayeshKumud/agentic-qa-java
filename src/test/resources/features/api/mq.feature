@api @mq @Sequential
Feature: ActiveMQ messages
  Sends, browses and reads queue messages with MqUtil. "out" is the out queue (mq.outQueue).
  Browse steps leave messages on the queue; read steps consume them.
  Skipped unless mq.enabled=true: mvn test -Dmq.enabled=true -Dcucumber.filter.tags="@mq"
  (MQ_URL, MQ_USERNAME, MQ_PASSWORD, MQ_OUT_QUEUE give the broker and queue).

  @PRGBATR-1019
  Scenario: Read all messages of the out queue
    Given the MQ queue "out" is empty
    When I send to the MQ queue "out":
      """
      {"orderId": "A-1"}
      """
    And I send to the MQ queue "out":
      """
      {"orderId": "A-2"}
      """
    And I browse all messages on the MQ queue "out"
    Then 2 MQ messages were returned
    And the MQ queue "out" has 2 messages
    When I read all messages from the MQ queue "out"
    Then 2 MQ messages were returned
    And one of the MQ messages contains "A-2"
    And the MQ queue "out" has 0 messages

  @PRGBATR-1020
  Scenario: Read one specific message by property, correlation id and text
    Given the MQ queue "out" is empty
    When I send to the MQ queue "out" with properties
      | orderId | 42                 |
      | body    | {"orderId": "42"}  |
    And I send to the MQ queue "out" with correlation id "CORR-7":
      """
      {"orderId": "7"}
      """
    And I send to the MQ queue "out":
      """
      {"orderId": "99"}
      """
    When I read the MQ message matching "orderId = '42'" from the MQ queue "out" within 10 seconds
    Then the MQ message property "orderId" is "42"
    When I read the MQ message with correlation id "CORR-7" from the MQ queue "out" within 10 seconds
    Then the MQ message contains "7"
    And the MQ queue "out" has a message containing "99" within 5 seconds
    When I read the MQ message containing "99" from the MQ queue "out" within 5 seconds
    Then the MQ message contains "99"
    And the MQ queue "out" has 0 messages
