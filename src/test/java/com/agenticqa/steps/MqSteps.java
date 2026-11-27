package com.agenticqa.steps;

import com.agenticqa.config.Settings;
import com.agenticqa.core.TestSession;
import com.agenticqa.core.Variables;
import com.agenticqa.mq.MqMessage;
import com.agenticqa.mq.MqUtil;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.agenticqa.steps.Keys.MQ_MESSAGE;
import static com.agenticqa.steps.Keys.MQ_MESSAGES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * ActiveMQ steps on top of {@link MqUtil}. A queue name of {@code "out"} means the out queue (mq.outQueue); any
 * other name is used as written. Texts may hold scenario variables such as {@code ${zoneId}}.
 * <p>
 * "browse" leaves messages on the queue; "read" consumes them. The last message is kept under
 * {@link Keys#MQ_MESSAGE}, the last list under {@link Keys#MQ_MESSAGES}, and the id of the last sent message in the
 * scenario variable {@code mqMessageId}. Scenarios tagged {@code @mq} are skipped unless mq.enabled=true.
 */
public class MqSteps {
    private static final Logger logger = LoggerFactory.getLogger(MqSteps.class);

    /** Scenario variable that holds the JMSMessageID of the last sent message. */
    static final String SENT_ID = "mqMessageId";

    private final TestSession session;
    private final Variables vars;
    private MqUtil mq;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param vars    the scenario's variables
     */
    public MqSteps(TestSession session, Variables vars) {
        this.session = session;
        this.vars = vars;
    }

    /** Skips {@code @mq} scenarios when no broker is configured (mq.enabled=false). */
    @Before(value = "@mq", order = 4)
    public void requireBroker() {
        if (!Settings.getBoolean("mq.enabled", false)) {
            throw new SkipException("mq.enabled is false: set MQ_ENABLED=true or -Dmq.enabled=true to run @mq scenarios");
        }
    }

    /** Closes the scenario's broker connection. */
    @After(value = "@mq", order = 8)
    public void closeConnection() {
        if (mq != null) {
            mq.close();
            mq = null;
        }
    }

    // ------------------------------------------------------------------ setup / send

    /** {@code Given the MQ queue "out" is empty}: removes every message on the queue. */
    @Given("the MQ queue {string} is empty")
    public void purge(String queue) {
        int removed = mq().purge(queueName(queue));
        logger.info("Removed {} message(s) from {}", removed, queueName(queue));
    }

    /**
     * Sends a text message:
     * <pre>
     * When I send to the MQ queue "IN.QUEUE":
     *   """
     *   {"orderId": "${orderId}"}
     *   """
     * </pre>
     */
    @When("I send to the MQ queue {string}:")
    public void send(String queue, String body) {
        remember(mq().send(queueName(queue), vars.resolve(body)));
    }

    /** Same as {@link #send} with a JMSCorrelationID: {@code When I send to the MQ queue "IN.QUEUE" with correlation id "C-1":}. */
    @When("I send to the MQ queue {string} with correlation id {string}:")
    public void sendWithCorrelationId(String queue, String correlationId, String body) {
        remember(mq().send(queueName(queue), vars.resolve(body), Map.of(), vars.resolve(correlationId)));
    }

    /**
     * Sends a message with properties (usable in selectors); the body is the "body" row:
     * <pre>
     * When I send to the MQ queue "IN.QUEUE" with properties
     *   | orderId | 42              |
     *   | body    | {"orderId": 42} |
     * </pre>
     */
    @When("I send to the MQ queue {string} with properties")
    public void sendWithProperties(String queue, Map<String, String> rows) {
        String body = vars.resolve(rows.getOrDefault("body", ""));
        Map<String, String> props = new java.util.LinkedHashMap<>();
        rows.forEach((k, v) -> {
            if (!k.equals("body")) {
                props.put(k, vars.resolve(v));
            }
        });
        remember(mq().send(queueName(queue), body, props));
    }

    // ------------------------------------------------------------------ browse (messages stay)

    /** {@code When I browse all messages on the MQ queue "out"}: stores them under MQ_MESSAGES, removes nothing. */
    @When("I browse all messages on the MQ queue {string}")
    public void browseAll(String queue) {
        keep(mq().browseAll(queueName(queue)));
    }

    /**
     * {@code Then the MQ queue "out" has a message containing "42" within 10 seconds}: waits without removing it;
     * the message is stored under MQ_MESSAGE.
     */
    @Then("the MQ queue {string} has a message containing {string} within {int} seconds")
    public void waitForContaining(String queue, String part, int seconds) {
        String text = vars.resolve(part);
        found(mq().waitForMessage(queueName(queue), m -> m.contains(text), seconds * 1000L).orElse(null),
                "message containing '" + text + "' on " + queueName(queue));
    }

    /** {@code Then the MQ queue "out" has 3 messages}: counts without removing. */
    @Then("the MQ queue {string} has {int} message(s)")
    public void count(String queue, int expected) {
        assertEquals(mq().count(queueName(queue)), expected, "messages on " + queueName(queue));
    }

    // ------------------------------------------------------------------ read (messages are consumed)

    /** {@code When I read all messages from the MQ queue "out"}: consumes them and stores them under MQ_MESSAGES. */
    @When("I read all messages from the MQ queue {string}")
    public void readAll(String queue) {
        keep(mq().readAll(queueName(queue)));
    }

    /**
     * Reads one message by JMS selector:
     * {@code When I read the MQ message matching "orderId = '42'" from the MQ queue "out" within 10 seconds}.
     */
    @When("I read the MQ message matching {string} from the MQ queue {string} within {int} seconds")
    public void readMatching(String selector, String queue, int seconds) {
        String sel = vars.resolve(selector);
        found(mq().readOne(queueName(queue), sel, seconds * 1000L).orElse(null), "message [" + sel + "] on " + queueName(queue));
    }

    /** {@code When I read the MQ message with correlation id "${mqMessageId}" from the MQ queue "out" within 10 seconds}. */
    @When("I read the MQ message with correlation id {string} from the MQ queue {string} within {int} seconds")
    public void readByCorrelationId(String correlationId, String queue, int seconds) {
        String id = vars.resolve(correlationId);
        found(mq().readByCorrelationId(queueName(queue), id, seconds * 1000L).orElse(null),
                "message with correlation id " + id + " on " + queueName(queue));
    }

    /**
     * Reads (consumes) the first message whose body contains the text; other messages stay on the queue:
     * {@code When I read the MQ message containing "42" from the MQ queue "out" within 10 seconds}.
     */
    @When("I read the MQ message containing {string} from the MQ queue {string} within {int} seconds")
    public void readContaining(String part, String queue, int seconds) {
        String text = vars.resolve(part);
        String q = queueName(queue);
        MqMessage match = mq().waitForMessage(q, m -> m.contains(text), seconds * 1000L).orElse(null);
        found(match == null ? null : mq().readByMessageId(q, match.messageId(), 2000).orElse(null),
                "message containing '" + text + "' on " + q);
    }

    // ------------------------------------------------------------------ checks

    /** {@code Then the MQ message contains "42"}: checks the body of the last message. */
    @Then("the MQ message contains {string}")
    public void messageContains(String part) {
        String text = vars.resolve(part);
        MqMessage m = session.data().get(MQ_MESSAGE);
        assertTrue(m.contains(text), "MQ message does not contain '" + text + "': " + m.text());
    }

    /** {@code Then the MQ message property "orderId" is "42"}. */
    @Then("the MQ message property {string} is {string}")
    public void messageProperty(String name, String expected) {
        Object value = session.data().get(MQ_MESSAGE).property(name);
        assertNotNull(value, "MQ message has no property " + name);
        assertEquals(String.valueOf(value), vars.resolve(expected), "MQ message property " + name);
    }

    /** {@code Then 3 MQ messages were returned}: size of the last read/browse list. */
    @Then("{int} MQ message(s) was/were returned")
    public void listSize(int expected) {
        assertEquals(session.data().get(MQ_MESSAGES).size(), expected, "MQ messages returned");
    }

    /** {@code Then one of the MQ messages contains "42"}: searches the last read/browse list. */
    @Then("one of the MQ messages contains {string}")
    public void anyContains(String part) {
        String text = vars.resolve(part);
        Predicate<MqMessage> match = m -> m.contains(text);
        assertTrue(session.data().get(MQ_MESSAGES).stream().anyMatch(match), "No MQ message contains '" + text + "'");
    }

    // ------------------------------------------------------------------ helpers

    private MqUtil mq() {
        if (mq == null) {
            mq = MqUtil.fromSettings();
        }
        return mq;
    }

    private String queueName(String queue) {
        String q = vars.resolve(queue);
        return q.equalsIgnoreCase("out") ? mq().outQueue() : q;
    }

    private void remember(String messageId) {
        vars.put(SENT_ID, messageId);
    }

    private void keep(List<MqMessage> messages) {
        session.data().put(MQ_MESSAGES, List.copyOf(messages));
        messages.forEach(m -> logger.info("MQ message {}: {}", m.messageId(), m.text()));
    }

    private void found(MqMessage message, String what) {
        if (message == null) {
            fail("No " + what);
        }
        session.data().put(MQ_MESSAGE, message);
        logger.info("MQ message {}: {}", message.messageId(), message.text());
    }
}
