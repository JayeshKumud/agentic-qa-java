# ActiveMQ steps

Feature files send, browse and read ActiveMQ queue messages with the steps in
[`MqSteps`](../src/test/java/com/agenticqa/steps/MqSteps.java). Each step calls
[`MqUtil`](../src/main/java/com/agenticqa/mq/MqUtil.java), which Java code can also use on its own (see the end of
this page). The example feature is
[`features/api/mq.feature`](../src/test/resources/features/api/mq.feature).

```gherkin
@api @mq @Sequential
Feature: Order messages

  @PRGBATR-1020
  Scenario: The order reply arrives on the out queue
    Given the MQ queue "out" is empty
    When I send to the MQ queue "out" with properties
      | orderId | 42                |
      | body    | {"orderId": "42"} |
    And I read the MQ message matching "orderId = '42'" from the MQ queue "out" within 10 seconds
    Then the MQ message property "orderId" is "42"
    And the MQ message contains "42"
```

## Browse or read

| | Browse | Read |
|---|---|---|
| What happens to the message | stays on the queue | is **removed** from the queue (a consumer takes it) |
| Use it for | looking at a queue that other systems also use, waiting for a message to appear | taking your own messages off the queue, clearing up after a test |

If other systems use the out queue, use the browse steps there. A read step takes the message away, so the other
systems never get it.

## Settings

The settings are in `config/common.properties`. Each one comes from an environment variable, and you can override
any of them for one run with `-D` (for example `-Dmq.url=...`).

| Key | Environment variable | Default | Meaning |
|---|---|---|---|
| `mq.enabled` | `MQ_ENABLED` | `false` | when false, `@mq` scenarios are **skipped**, so runs without a broker don't fail |
| `mq.url` | `MQ_URL` | `tcp://localhost:61616` | broker URL (`tcp://` or `ssl://`) |
| `mq.username` | `MQ_USERNAME` | `admin` | user name |
| `mq.password` | `MQ_PASSWORD` | `admin` | password: keep it in the environment variable, not in a file |
| `mq.outQueue` | `MQ_OUT_QUEUE` | `OUT.QUEUE` | the queue the steps mean by `"out"` |
| `mq.receiveTimeoutMs` | | `2000` | how long "read all" waits for the next message before it stops |

## Running

```powershell
$env:MQ_URL="tcp://host:61616"; $env:MQ_USERNAME="user"; $env:MQ_PASSWORD="secret"; $env:MQ_OUT_QUEUE="MY.OUT.QUEUE"
mvn test -Dtest=TestRunner -Dmq.enabled=true -Dcucumber.filter.tags="@mq"
```

Tag MQ scenarios `@mq`, which turns the skip on when `mq.enabled=false` and closes the connection after the scenario.
Also tag them `@Sequential` when they share a queue, so parallel scenarios don't read each other's messages.

**Warning:** the scenarios in `mq.feature` start with `Given the MQ queue "out" is empty`, which deletes every
message on the out queue. Point `MQ_OUT_QUEUE` at a test queue before you run them.

## Steps

**Queue names:** write `"out"` for the out queue (`mq.outQueue`); any other name is used as written. Queue names,
bodies, selectors and expected texts can use scenario variables such as `${zoneId}`.

### Setup and send

| Step | What it does |
|---|---|
| `Given the MQ queue "out" is empty` | removes every message on the queue |
| `When I send to the MQ queue "IN.QUEUE":` + text block | sends the text block as a text message |
| `When I send to the MQ queue "IN.QUEUE" with correlation id "C-1":` + text block | the same, with a `JMSCorrelationID` |
| `When I send to the MQ queue "IN.QUEUE" with properties` + table | sends message properties (which selectors can match); the `body` row is the message text |

```gherkin
When I send to the MQ queue "IN.QUEUE":
  """
  {"orderId": "${orderId}", "zone": "${zoneId}"}
  """
When I send to the MQ queue "IN.QUEUE" with properties
  | orderId   | 42                |
  | eventType | ORDER_CREATED     |
  | body      | {"orderId": "42"} |
```

Each send stores the `JMSMessageID` of the message in the scenario variable `${mqMessageId}`. Systems that reply
with the request's id as the correlation id can then be read with
`When I read the MQ message with correlation id "${mqMessageId}" from the MQ queue "out" within 10 seconds`.

### Browse (messages stay on the queue)

| Step | What it does |
|---|---|
| `When I browse all messages on the MQ queue "out"` | saves every message now on the queue as the message list |
| `Then the MQ queue "out" has 2 messages` | counts the messages now on the queue (`1 message` also works) |
| `Then the MQ queue "out" has a message containing "42" within 10 seconds` | checks the queue every 0.5 s until a message body contains the text; saves it as the message |

### Read (messages are removed)

| Step | What it does |
|---|---|
| `When I read all messages from the MQ queue "out"` | reads messages until none comes for `mq.receiveTimeoutMs`; saves them as the message list |
| `When I read the MQ message matching "orderId = '42'" from the MQ queue "out" within 10 seconds` | reads the first message the [JMS selector](#selectors) matches |
| `When I read the MQ message with correlation id "C-1" from the MQ queue "out" within 10 seconds` | reads the message with that `JMSCorrelationID` |
| `When I read the MQ message containing "99" from the MQ queue "out" within 10 seconds` | waits for a message whose body contains the text and reads only that one; the other messages stay on the queue |

Each read step that reads one message fails if no message arrives within the time given.

### Checks

| Step | Checks |
|---|---|
| `Then the MQ message contains "42"` | the body of the saved message |
| `Then the MQ message property "orderId" is "42"` | a property of the saved message |
| `Then 2 MQ messages were returned` | the size of the saved message list (`1 MQ message was returned` also works) |
| `Then one of the MQ messages contains "A-2"` | at least one body in the saved message list contains the text |

## Selectors

A selector is a JMS message selector, which works like an SQL `WHERE` clause on a message's headers and properties
(not its body). Selectors are case-sensitive:

```text
orderId = '42'
eventType = 'ORDER_CREATED' AND zone = 'Z1'
JMSCorrelationID = 'C-1'
JMSType = 'Order'
priority > 4
```

To find a message by text in its body, use the `containing` steps instead.

## Using the data in other step classes

The steps save what they find in the scenario's session (see [keys.md](keys.md)):

| Key | Value |
|---|---|
| `Keys.MQ_MESSAGE` | the last message a step read or found (`MqMessage`) |
| `Keys.MQ_MESSAGES` | the list from the last "browse all" or "read all" step (`List<MqMessage>`) |

```java
MqMessage reply = session.data().get(Keys.MQ_MESSAGE);
String orderId = (String) reply.property("orderId");
String body = reply.text();            // TextMessage text, or a BytesMessage read as UTF-8
```

## MqUtil in Java

```java
try (MqUtil mq = MqUtil.fromSettings()) {                        // or new MqUtil(url, user, password, "OUT.QUEUE")
    List<MqMessage> all = mq.browseAll(mq.outQueue());           // look, leave on the queue
    List<MqMessage> read = mq.readAll(mq.outQueue());            // read and remove all
    String id = mq.send("IN.QUEUE", "{\"orderId\":42}", Map.of("orderId", "42"));
    Optional<MqMessage> one = mq.readOne(mq.outQueue(), "orderId = '42'", 10_000);
    Optional<MqMessage> reply = mq.readByCorrelationId(mq.outQueue(), id, 10_000);
    Optional<MqMessage> seen = mq.waitForMessage(mq.outQueue(), m -> m.contains("42"), 10_000);
}
```

| Method | Removes messages? |
|---|---|
| `browseAll`, `browse(queue, selector)`, `count`, `waitForMessage` | no |
| `readAll`, `readAll(queue, selector)`, `readOne`, `readByCorrelationId`, `readByMessageId`, `purge` | yes |
| `send(queue, text)`, `send(queue, text, properties)`, `send(queue, text, properties, correlationId)` | (sends; returns the `JMSMessageID`) |

Each `MqUtil` holds one connection, and each call opens its own session. Close the instance when you're done
(`MqSteps` closes it after each `@mq` scenario). The client is ActiveMQ Classic (`activemq-client`, `javax.jms`);
it also connects to ActiveMQ Artemis through its OpenWire port (61616 by default).
