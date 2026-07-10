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
 * Tests for {@link DefaultJmsPublishObservationConvention}.
 *
 * @author Brian Clozel
 */
class DefaultJmsPublishObservationConventionTests {

    private final DefaultJmsPublishObservationConvention convention = new DefaultJmsPublishObservationConvention();

    @Test
    void shouldHaveObservationName() {
        assertThat(convention.getName()).isEqualTo("jms.message.publish");
    }

    @Test
    void shouldHaveQueueContextualName() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithQueue());
        assertThat(convention.getContextualName(context)).isEqualTo("micrometer.test.queue publish");
    }

    @Test
    void shouldHaveTopicContextualName() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithTopic());
        assertThat(convention.getContextualName(context)).isEqualTo("micrometer.test.topic publish");
    }

    @Test
    void shouldHaveOperationName() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithQueue());
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.operation", "publish"));
    }

    @Test
    void shouldHaveCorrelationIdWhenAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        when(message.getJMSCorrelationID()).thenReturn("test-correlation");
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "test-correlation"));
    }

    @Test
    void shouldHaveUnknownCorrelationIdWhenNotAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "unknown"));
    }

    @Test
    void shouldHaveUnknownCorrelationIdWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        when(message.getJMSCorrelationID()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.conversation_id", "unknown"));
    }

    @Test
    void shouldHaveQueueDestinationName() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithQueue());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "micrometer.test.queue"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenQueueNameIsNull() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithNullQueueName());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveTopicDestinationName() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithTopic());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "micrometer.test.topic"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenTopicNameIsNull() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(createMessageWithNullTopicName());
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveUnknownDestinationNameWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        when(((Queue) message.getJMSDestination()).getQueueName()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.name", "unknown"));
    }

    @Test
    void shouldHaveMessageIdWhenAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        when(message.getJMSMessageID()).thenReturn("test-id");
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "test-id"));
    }

    @Test
    void shouldHaveUnknownMessageIdWhenNotAvailable() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "unknown"));
    }

    @Test
    void shouldHaveUnknownMessageIdWhenException() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        when(message.getJMSMessageID()).thenThrow(new JMSException("test exception"));
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.message.id", "unknown"));
    }

    @Test
    void shouldHaveDurableDestinationForQueue() throws Exception {
        Message message = createMessageWithQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "false"));
    }

    @Test
    void shouldHaveTempDestinationForTemporaryQueue() throws Exception {
        Message message = createMessageWithTempQueue();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "true"));
    }

    @Test
    void shouldHaveDurableDestinationForTopic() throws Exception {
        Message message = createMessageWithTopic();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "false"));
    }

    @Test
    void shouldHaveTempDestinationForTemporaryTopic() throws Exception {
        Message message = createMessageWithTempTopic();
        JmsPublishObservationContext context = new JmsPublishObservationContext(message);
        assertThat(convention.getLowCardinalityKeyValues(context))
            .contains(KeyValue.of("messaging.destination.temporary", "true"));
    }

    @Test
    void shouldHaveTopicDestinationNameEvenWhenTheTopicAlsoImplementsTheQueueInterface() throws Exception {
        JmsPublishObservationContext context = new JmsPublishObservationContext(
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
