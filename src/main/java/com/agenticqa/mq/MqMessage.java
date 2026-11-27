package com.agenticqa.mq;

import java.util.Map;

/**
 * One queue message, copied out of JMS so it stays readable after the session is closed.
 *
 * @param messageId     JMSMessageID
 * @param correlationId JMSCorrelationID, or null
 * @param type          JMSType, or null
 * @param timestamp     JMSTimestamp (epoch milliseconds)
 * @param text          body of a TextMessage, or a BytesMessage read as UTF-8; null for other message kinds
 * @param properties    the message's own properties (name to value)
 */
public record MqMessage(String messageId, String correlationId, String type, long timestamp, String text,
                        Map<String, Object> properties) {

    /** Value of a message property, or null. */
    public Object property(String name) {
        return properties.get(name);
    }

    /** Tells whether the body contains the given text. */
    public boolean contains(String part) {
        return text != null && text.contains(part);
    }
}
