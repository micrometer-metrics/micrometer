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
import jakarta.jms.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultJmsProcessObservationConvention}.
 *
 * @author Brian Clozel
 */
class DefaultJmsProcessObservationConventionTests {

    private final DefaultJmsProcessObservationConvention convention = new DefaultJmsProcessObservationConvention();

    @Test
    void shouldHaveObservationName() {
        assertThat(convention.getName()).isEqualTo("jms.message.process");
    }

    @Test
    void shouldHaveQueueContextualName() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithQueue());
        assertThat(convention.getContextualName(context)).isEqualTo("micrometer.test.queue process");
    }

    @Test
    void shouldHaveTopicContextualName() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithTopic());
        assertThat(convention.getContextualName(context)).isEqualTo("micrometer.test.topic process");
    }

    @Test
    void shouldHaveOperationName() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithQueue());
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.operation", "process"));
    }

    @Test
    void shouldHaveCorrelationIdWhenAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        when(message.getJMSCorrelationID()).thenReturn("test-correlation");
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "test-correlation"));
    }

    @Test
    void shouldHaveUnknownCorrelationIdWhenNotAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "unknown"));
    }

    @Test
    void shouldHaveUnknownCorrelationIdWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        when(message.getJMSCorrelationID()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "unknown"));
    }

    @Test
    void shouldHaveQueueDestinationName() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithQueue());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "micrometer.test.queue"));
    }

    @Test
    void shouldHaveTopicDestinationName() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithTopic());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "micrometer.test.topic"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenTopicNameIsNull() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithNullTopicName());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenQueueNameIsNull() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(createMessageWithNullQueueName());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        when(((Queue) message.getJMSDestination()).getQueueName()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveMessageIdWhenAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        when(message.getJMSMessageID()).thenReturn("test-id");
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "test-id"));
    }

    @Test
    void shouldHaveUnknownMessageIdWhenNotAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "unknown"));
    }

    @Test
    void shouldHaveUnknownMessageIdWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        when(message.getJMSMessageID()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "unknown"));
    }

    @Test
    void shouldHaveDurableDestinationForQueue() throws Exception {
        Message message = createMessageWithQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "false"));
    }

    @Test
    void shouldHaveTempDestinationForTemporaryQueue() throws Exception {
        Message message = createMessageWithTempQueue();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "true"));
    }

    @Test
    void shouldHaveDurableDestinationForTopic() throws Exception {
        Message message = createMessageWithTopic();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "false"));
    }

    @Test
    void shouldHaveTempDestinationForTemporaryTopic() throws Exception {
        Message message = createMessageWithTempTopic();
        JmsProcessObservationContext context = new JmsProcessObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "true"));
    }

    @Test
    void shouldHaveTopicDestinationNameEvenWhenTheTopicAlsoImplementsTheQueueInterface() throws Exception {
        JmsProcessObservationContext context = new JmsProcessObservationContext(
                createMessageWithTopicThatAlsoImplementsTheQueueInterface());

        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "micrometer.test.topic"));
    }

    private Message createMessageWithTopicThatAlsoImplementsTheQueueInterface() throws Exception {
        Topic topic = mock(Topic.class, withSettings().extraInterfaces(Queue.class));
        when(topic.getTopicName()).thenReturn("micrometer.test.topic");
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(topic);
        return message;
    }

    private Message createMessageWithQueue() throws Exception {
        Queue queue = mock(Queue.class);
        when(queue.getQueueName()).thenReturn("micrometer.test.queue");
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(queue);
        return message;
    }

    private Message createMessageWithNullQueueName() throws Exception {
        Queue queue = mock(Queue.class);
        when(queue.getQueueName()).thenReturn(null);
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(queue);
        return message;
    }

    private Message createMessageWithTempQueue() throws Exception {
        TemporaryQueue queue = mock(TemporaryQueue.class);
        when(queue.getQueueName()).thenReturn("micrometer.test.queue");
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(queue);
        return message;
    }

    private Message createMessageWithTopic() throws Exception {
        Topic topic = mock(Topic.class);
        when(topic.getTopicName()).thenReturn("micrometer.test.topic");
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(topic);
        return message;
    }

    private Message createMessageWithNullTopicName() throws Exception {
        Topic topic = mock(Topic.class);
        when(topic.getTopicName()).thenReturn(null);
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(topic);
        return message;
    }

    private Message createMessageWithTempTopic() throws Exception {
        TemporaryTopic topic = mock(TemporaryTopic.class);
        when(topic.getTopicName()).thenReturn("micrometer.test.topic");
        Message message = mock(Message.class);
        when(message.getJMSDestination()).thenReturn(topic);
        return message;
    }

}
