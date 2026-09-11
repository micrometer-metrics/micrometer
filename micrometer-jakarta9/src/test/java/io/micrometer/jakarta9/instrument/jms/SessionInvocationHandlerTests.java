/*
 * Copyright 2026 VMware, Inc.
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

import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the proxies created by {@link JmsInstrumentation#instrumentSession}.
 *
 * @author Kumar Gaurav
 */
class SessionInvocationHandlerTests {

    private final ObservationRegistry registry = ObservationRegistry.create();

    @Test
    void shouldReturnProxyAssignableToDeclaredReturnType() throws JMSException {
        Session session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(session.createDurableSubscriber(topic, "sub")).thenReturn(mock(TopicSubscriber.class));

        Session instrumented = JmsInstrumentation.instrumentSession(session, this.registry);

        assertThatCode(() -> instrumented.createDurableSubscriber(topic, "sub")).doesNotThrowAnyException();
        assertThat(instrumented.createDurableSubscriber(topic, "sub")).isInstanceOf(TopicSubscriber.class);
    }

    @Test
    void shouldStillProxyPlainConsumers() throws JMSException {
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createConsumer(queue)).thenReturn(mock(MessageConsumer.class));

        Session instrumented = JmsInstrumentation.instrumentSession(session, this.registry);

        assertThat(instrumented.createConsumer(queue)).isInstanceOf(MessageConsumer.class);
    }

    @Test
    void shouldStillProxyProducers() throws JMSException {
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createProducer(queue)).thenReturn(mock(MessageProducer.class));

        Session instrumented = JmsInstrumentation.instrumentSession(session, this.registry);

        assertThat(instrumented.createProducer(queue)).isInstanceOf(MessageProducer.class);
    }

}
