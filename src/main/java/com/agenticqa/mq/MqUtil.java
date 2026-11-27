package com.agenticqa.mq;

import com.agenticqa.config.Settings;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * ActiveMQ helper: browse, read (consume) and send queue messages.
 *
 * <p><b>Browse</b> methods look at messages and leave them on the queue. <b>Read</b> methods consume them: a read
 * message is gone from the queue. Selectors are JMS message selectors, for example
 * {@code "JMSCorrelationID = 'abc'"} or {@code "orderId = '42'"} (a message property).
 *
 * <pre>{@code
 * try (MqUtil mq = MqUtil.fromSettings()) {
 *     List<MqMessage> all = mq.browseAll(mq.outQueue());          // look, keep on queue
 *     List<MqMessage> read = mq.readAll(mq.outQueue());           // consume everything
 *     String id = mq.send("IN.QUEUE", "{\"orderId\":42}", Map.of("orderId", "42"));
 *     MqMessage reply = mq.readOne(mq.outQueue(), "JMSCorrelationID = '" + id + "'", 10_000).orElseThrow();
 *     MqMessage match = mq.waitForMessage(mq.outQueue(), m -> m.contains("42"), 10_000).orElseThrow();
 * }
 * }</pre>
 *
 * One instance holds one connection; each call opens and closes its own session. Close the instance when done.
 */
public class MqUtil implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MqUtil.class);
    private static final long POLL_MS = 500;

    private final Connection connection;
    private final String outQueue;
    private final long receiveTimeoutMs;

    /**
     * Connects to the broker.
     *
     * @param url      broker URL, e.g. tcp://host:61616
     * @param username user name
     * @param password password
     * @param outQueue default out queue name (may be null)
     */
    public MqUtil(String url, String username, String password, String outQueue) {
        this(url, username, password, outQueue, 2000);
    }

    /** Same as {@link #MqUtil(String, String, String, String)} with the wait per read in milliseconds. */
    public MqUtil(String url, String username, String password, String outQueue, long receiveTimeoutMs) {
        this.outQueue = outQueue;
        this.receiveTimeoutMs = receiveTimeoutMs;
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(username, password, url);
            factory.setTrustAllPackages(true);
            this.connection = factory.createConnection();
            this.connection.start();
            log.info("Connected to ActiveMQ {}", url);
        } catch (JMSException e) {
            throw new IllegalStateException("Cannot connect to ActiveMQ " + url + ": " + e.getMessage(), e);
        }
    }

    /** Connects with mq.url, mq.username, mq.password, mq.outQueue and mq.receiveTimeoutMs from the settings. */
    public static MqUtil fromSettings() {
        return new MqUtil(Settings.require("mq.url"), Settings.get("mq.username"), Settings.get("mq.password"),
                Settings.get("mq.outQueue"), Settings.getInt("mq.receiveTimeoutMs", 2000));
    }

    /** The default out queue (mq.outQueue). */
    public String outQueue() {
        if (outQueue == null || outQueue.isBlank()) {
            throw new IllegalStateException("No out queue name given (mq.outQueue)");
        }
        return outQueue;
    }

    // ------------------------------------------------------------------ browse (messages stay on the queue)

    /** All messages now on the queue, oldest first; nothing is removed. */
    public List<MqMessage> browseAll(String queue) {
        return browse(queue, null);
    }

    /** Messages on the queue that match the JMS selector; nothing is removed. */
    public List<MqMessage> browse(String queue, String selector) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Queue q = session.createQueue(queue);
            List<MqMessage> messages = new ArrayList<>();
            try (QueueBrowser browser = selector == null ? session.createBrowser(q) : session.createBrowser(q, selector)) {
                Enumeration<?> e = browser.getEnumeration();
                while (e.hasMoreElements()) {
                    messages.add(toMqMessage((Message) e.nextElement()));
                }
            }
            log.info("Browsed {} message(s) on {}{}", messages.size(), queue, selector == null ? "" : " [" + selector + "]");
            return messages;
        } catch (JMSException e) {
            throw new IllegalStateException("Cannot browse " + queue + ": " + e.getMessage(), e);
        }
    }

    /** Number of messages now on the queue. */
    public int count(String queue) {
        return browseAll(queue).size();
    }

    /**
     * Browses the queue until a message matches, without removing it.
     *
     * @return the first matching message, or empty after the timeout
     */
    public Optional<MqMessage> waitForMessage(String queue, Predicate<MqMessage> match, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        do {
            Optional<MqMessage> found = browseAll(queue).stream().filter(match).findFirst();
            if (found.isPresent()) {
                return found;
            }
            sleep(POLL_MS);
        } while (System.currentTimeMillis() < end);
        log.warn("No matching message on {} within {} ms", queue, timeoutMs);
        return Optional.empty();
    }

    // ------------------------------------------------------------------ read (consumer: messages are removed)

    /** Reads (removes) every message on the queue until none arrives within the receive timeout. */
    public List<MqMessage> readAll(String queue) {
        return read(queue, null, Integer.MAX_VALUE);
    }

    /** Reads (removes) every message that matches the JMS selector; other messages stay on the queue. */
    public List<MqMessage> readAll(String queue, String selector) {
        return read(queue, selector, Integer.MAX_VALUE);
    }

    /**
     * Reads (removes) one message that matches the JMS selector (null = any message).
     *
     * @return the message, or empty if none arrived within the timeout
     */
    public Optional<MqMessage> readOne(String queue, String selector, long timeoutMs) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageConsumer consumer = consumer(session, queue, selector)) {
            Message message = consumer.receive(timeoutMs);
            log.info("Read {} message from {}{}", message == null ? "no" : "1", queue, selector == null ? "" : " [" + selector + "]");
            return message == null ? Optional.empty() : Optional.of(toMqMessage(message));
        } catch (JMSException e) {
            throw new IllegalStateException("Cannot read from " + queue + ": " + e.getMessage(), e);
        }
    }

    /** Reads (removes) the message with the given correlation id. */
    public Optional<MqMessage> readByCorrelationId(String queue, String correlationId, long timeoutMs) {
        return readOne(queue, "JMSCorrelationID = '" + correlationId.replace("'", "''") + "'", timeoutMs);
    }

    /** Reads (removes) the message with the given message id. */
    public Optional<MqMessage> readByMessageId(String queue, String messageId, long timeoutMs) {
        return readOne(queue, "JMSMessageID = '" + messageId.replace("'", "''") + "'", timeoutMs);
    }

    /** Removes every message on the queue; returns how many were removed. */
    public int purge(String queue) {
        return readAll(queue).size();
    }

    private List<MqMessage> read(String queue, String selector, int max) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageConsumer consumer = consumer(session, queue, selector)) {
            List<MqMessage> messages = new ArrayList<>();
            Message message;
            while (messages.size() < max && (message = consumer.receive(receiveTimeoutMs)) != null) {
                messages.add(toMqMessage(message));
            }
            log.info("Read {} message(s) from {}{}", messages.size(), queue, selector == null ? "" : " [" + selector + "]");
            return messages;
        } catch (JMSException e) {
            throw new IllegalStateException("Cannot read from " + queue + ": " + e.getMessage(), e);
        }
    }

    private static MessageConsumer consumer(Session session, String queue, String selector) throws JMSException {
        Queue q = session.createQueue(queue);
        return selector == null ? session.createConsumer(q) : session.createConsumer(q, selector);
    }

    // ------------------------------------------------------------------ send (producer)

    /** Sends a text message; returns its JMSMessageID. */
    public String send(String queue, String text) {
        return send(queue, text, Map.of(), null);
    }

    /** Sends a text message with string/number/boolean properties; returns its JMSMessageID. */
    public String send(String queue, String text, Map<String, ?> properties) {
        return send(queue, text, properties, null);
    }

    /**
     * Sends a text message.
     *
     * @param properties    message properties (usable in selectors)
     * @param correlationId JMSCorrelationID, or null
     * @return the JMSMessageID
     */
    public String send(String queue, String text, Map<String, ?> properties, String correlationId) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(queue))) {
            TextMessage message = session.createTextMessage(text);
            if (correlationId != null) {
                message.setJMSCorrelationID(correlationId);
            }
            for (Map.Entry<String, ?> p : properties.entrySet()) {
                message.setObjectProperty(p.getKey(), p.getValue());
            }
            producer.send(message);
            log.info("Sent message {} to {}", message.getJMSMessageID(), queue);
            return message.getJMSMessageID();
        } catch (JMSException e) {
            throw new IllegalStateException("Cannot send to " + queue + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static MqMessage toMqMessage(Message message) throws JMSException {
        String text = null;
        if (message instanceof TextMessage t) {
            text = t.getText();
        } else if (message instanceof BytesMessage b) {
            byte[] bytes = new byte[(int) b.getBodyLength()];
            b.readBytes(bytes);
            b.reset();
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        Enumeration<?> names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            properties.put(name, message.getObjectProperty(name));
        }
        return new MqMessage(message.getJMSMessageID(), message.getJMSCorrelationID(), message.getJMSType(),
                message.getJMSTimestamp(), text, Collections.unmodifiableMap(properties));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (JMSException e) {
            log.warn("Closing the ActiveMQ connection failed: {}", e.getMessage());
        }
    }
}
