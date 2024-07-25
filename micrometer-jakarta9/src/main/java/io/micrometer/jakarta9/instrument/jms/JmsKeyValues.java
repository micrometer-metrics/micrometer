/*
 * Copyright 2024 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.jakarta9.instrument.jms;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import jakarta.jms.*;

class JmsKeyValues {

    private static final KeyValue DESTINATION_TEMPORARY = KeyValue
        .of(JmsObservationDocumentation.LowCardinalityKeyNames.DESTINATION_TEMPORARY, "true");

    private static final KeyValue DESTINATION_DURABLE = KeyValue
        .of(JmsObservationDocumentation.LowCardinalityKeyNames.DESTINATION_TEMPORARY, "false");

    private static final KeyValue MESSAGE_CONVERSATION_ID_UNKNOWN = KeyValue
        .of(JmsObservationDocumentation.HighCardinalityKeyNames.CONVERSATION_ID, "unknown");

    private static final KeyValue DESTINATION_NAME_UNKNOWN = KeyValue
        .of(JmsObservationDocumentation.HighCardinalityKeyNames.DESTINATION_NAME, "unknown");

    private static final KeyValue MESSAGE_ID_UNKNOWN = KeyValue
        .of(JmsObservationDocumentation.HighCardinalityKeyNames.MESSAGE_ID, "unknown");

    private JmsKeyValues() {
    }

    static KeyValue conversationId(@Nullable Message message) {
        try {
            if (message == null || message.getJMSCorrelationID() == null) {
                return MESSAGE_CONVERSATION_ID_UNKNOWN;
            }
            return KeyValue.of(JmsObservationDocumentation.HighCardinalityKeyNames.CONVERSATION_ID,
                    message.getJMSCorrelationID());
        }
        catch (JMSException exc) {
            return MESSAGE_CONVERSATION_ID_UNKNOWN;
        }
    }

    static KeyValue destinationName(@Nullable Message message) {
        if (message == null) {
            return DESTINATION_NAME_UNKNOWN;
        }
        try {
            Destination destination = message.getJMSDestination();
            if (destination instanceof Queue) {
                Queue queue = (Queue) destination;
                String queueName = queue.getQueueName();
                if (queueName != null) {
                    return KeyValue.of(JmsObservationDocumentation.HighCardinalityKeyNames.DESTINATION_NAME, queueName);
                }
            }
            return getKeyValueTopic(destination);
        }
        catch (JMSException e) {
            return DESTINATION_NAME_UNKNOWN;
        }
    }

    static KeyValue messageId(@Nullable Message message) {
        try {
            if (message == null || message.getJMSMessageID() == null) {
                return MESSAGE_ID_UNKNOWN;
            }
            return KeyValue.of(JmsObservationDocumentation.HighCardinalityKeyNames.MESSAGE_ID,
                    message.getJMSMessageID());
        }
        catch (JMSException exc) {
            return MESSAGE_ID_UNKNOWN;
        }
    }

    static KeyValue temporaryDestination(@Nullable Message message) {
        try {
            if (message != null) {
                Destination destination = message.getJMSDestination();
                if (destination instanceof TemporaryQueue || destination instanceof TemporaryTopic) {
                    return DESTINATION_TEMPORARY;
                }
            }
            return DESTINATION_DURABLE;
        }
        catch (JMSException exc) {
            return DESTINATION_DURABLE;
        }
    }

    private static KeyValue getKeyValueTopic(Destination jmsDestination) throws JMSException {
        if (jmsDestination instanceof Topic) {
            Topic topic = (Topic) jmsDestination;
            String topicName = topic.getTopicName();
            if (topicName != null) {
                return KeyValue.of(JmsObservationDocumentation.HighCardinalityKeyNames.DESTINATION_NAME, topicName);
            }
        }
        return DESTINATION_NAME_UNKNOWN;
    }

}
