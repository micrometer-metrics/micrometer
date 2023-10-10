/*
 * Copyright 2023 VMware, Inc.
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
import io.micrometer.common.KeyValues;
import jakarta.jms.*;

import io.micrometer.jakarta9.instrument.jms.JmsObservationDocumentation.*;

/**
 * Default implementation for {@link JmsProcessObservationConvention}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class DefaultJmsProcessObservationConvention implements JmsProcessObservationConvention {

    private static final KeyValue DESTINATION_TEMPORARY = KeyValue.of(LowCardinalityKeyNames.DESTINATION_TEMPORARY,
            "true");

    private static final KeyValue DESTINATION_DURABLE = KeyValue.of(LowCardinalityKeyNames.DESTINATION_TEMPORARY,
            "false");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue OPERATION_PROCESS = KeyValue.of(LowCardinalityKeyNames.OPERATION, "process");

    private static final KeyValue DESTINATION_NAME_UNKNOWN = KeyValue.of(HighCardinalityKeyNames.DESTINATION_NAME,
            "unknown");

    private static final KeyValue MESSAGE_CONVERSATION_ID_UNKNOWN = KeyValue.of(HighCardinalityKeyNames.CONVERSATION_ID,
            "unknown");

    private static final KeyValue MESSAGE_ID_UNKNOWN = KeyValue.of(HighCardinalityKeyNames.MESSAGE_ID, "unknown");

    @Override
    public String getName() {
        return "jms.message.process";
    }

    @Override
    public String getContextualName(JmsProcessObservationContext context) {
        return destinationName(context).getValue() + " process";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(JmsProcessObservationContext context) {
        return KeyValues.of(exception(context), OPERATION_PROCESS, temporaryDestination(context));
    }

    private KeyValue exception(JmsProcessObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(LowCardinalityKeyNames.EXCEPTION,
                    !simpleName.isEmpty() ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    protected KeyValue temporaryDestination(JmsProcessObservationContext context) {
        try {
            Message message = context.getCarrier();
            Destination destination = message.getJMSDestination();
            if (destination instanceof TemporaryQueue || destination instanceof TemporaryTopic) {
                return DESTINATION_TEMPORARY;
            }
            return DESTINATION_DURABLE;
        }
        catch (JMSException exc) {
            return DESTINATION_DURABLE;
        }
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(JmsProcessObservationContext context) {
        return KeyValues.of(correlationId(context), destinationName(context), messageId(context));
    }

    protected KeyValue correlationId(JmsProcessObservationContext context) {
        try {
            Message message = context.getCarrier();
            if (message.getJMSCorrelationID() == null) {
                return MESSAGE_CONVERSATION_ID_UNKNOWN;
            }
            return KeyValue.of(HighCardinalityKeyNames.CONVERSATION_ID, message.getJMSCorrelationID());
        }
        catch (JMSException exc) {
            return MESSAGE_CONVERSATION_ID_UNKNOWN;
        }
    }

    protected KeyValue destinationName(JmsProcessObservationContext context) {
        try {
            Destination jmsDestination = context.getCarrier().getJMSDestination();
            if (jmsDestination instanceof Queue) {
                Queue queue = (Queue) jmsDestination;
                return KeyValue.of(HighCardinalityKeyNames.DESTINATION_NAME, queue.getQueueName());
            }
            if (jmsDestination instanceof Topic) {
                Topic topic = (Topic) jmsDestination;
                return KeyValue.of(HighCardinalityKeyNames.DESTINATION_NAME, topic.getTopicName());
            }
            return DESTINATION_NAME_UNKNOWN;
        }
        catch (JMSException e) {
            return DESTINATION_NAME_UNKNOWN;
        }
    }

    protected KeyValue messageId(JmsProcessObservationContext context) {
        try {
            Message message = context.getCarrier();
            if (message.getJMSMessageID() == null) {
                return MESSAGE_ID_UNKNOWN;
            }
            return KeyValue.of(HighCardinalityKeyNames.MESSAGE_ID, message.getJMSMessageID());
        }
        catch (JMSException exc) {
            return MESSAGE_ID_UNKNOWN;
        }
    }

}
