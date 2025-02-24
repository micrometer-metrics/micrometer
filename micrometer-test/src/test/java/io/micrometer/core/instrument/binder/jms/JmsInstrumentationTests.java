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
package io.micrometer.core.instrument.binder.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.micrometer.jakarta9.instrument.jms.JmsInstrumentation;
import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.junit.EmbeddedActiveMQExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JmsInstrumentation}.
 *
 * @author Brian Clozel
 */
class JmsInstrumentationTests {

    @RegisterExtension
    EmbeddedActiveMQExtension server = new EmbeddedActiveMQExtension();

    private TestObservationRegistry registry = TestObservationRegistry.create();

    private ActiveMQConnectionFactory connectionFactory;

    private Connection jmsConnection;

    @BeforeEach
    void setupServer() throws JMSException {
        server.start();
        connectionFactory = new ActiveMQConnectionFactory(server.getVmURL());
        jmsConnection = connectionFactory.createConnection();
        jmsConnection.start();
    }

    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("messageSenders")
    void shouldInstrumentSendOperations(String methodName, SessionConsumer sessionConsumer) throws Exception {
        try (Session session = createInstrumentedSession()) {
            sessionConsumer.accept(session);
            assertThat(registry).hasObservationWithNameEqualTo("jms.message.publish")
                .that()
                .hasContextualNameEqualTo("test.send publish");
        }
    }

    static Stream<Arguments> messageSenders() {
        return Stream.of(Arguments.of("send(Message)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test content"));
        }), Arguments.of("send(Message, int, int, int)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test content"), 1, 1, 1);
        }), Arguments.of("send(Destination, Message)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(null);
            producer.send(topic, session.createTextMessage("test content"));
        }), Arguments.of("send(Destination, Message, int, int, int)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(null);
            producer.send(topic, session.createTextMessage("test content"), 1, 1, 1);
        }), Arguments.of("send(Message, CompletionListener)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test content"), new NoOpCompletionListener());
        }), Arguments.of("send(Message, int, int, int, CompletionListener)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test content"), 1, 1, 1, new NoOpCompletionListener());
        }), Arguments.of("send(Destination, Message, CompletionListener)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(null);
            producer.send(topic, session.createTextMessage("test content"), new NoOpCompletionListener());
        }), Arguments.of("send(Destination, Message, int, int, int, CompletionListener)", (SessionConsumer) session -> {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(null);
            producer.send(topic, session.createTextMessage("test content"), 1, 1, 1, new NoOpCompletionListener());
        }));
    }

    @Test
    void shouldInstrumentSendOperationWhenException() throws Exception {
        try (Session session = createInstrumentedSession()) {
            Topic topic = session.createTopic("test.send");
            MessageProducer producer = session.createProducer(topic);
            TextMessage message = session.createTextMessage("test content");
            jmsConnection.close();
            assertThatThrownBy(() -> producer.send(message)).isInstanceOf(jakarta.jms.IllegalStateException.class);
            assertThat(registry).hasObservationWithNameEqualTo("jms.message.publish")
                .that()
                .hasContextualNameEqualTo("test.send publish")
                .hasLowCardinalityKeyValue("exception", "IllegalStateException");
        }
    }

    @Test
    void shouldInstrumentMessageListener() throws Exception {
        try (Session session = createInstrumentedSession()) {
            Topic topic = session.createTopic("test.send");
            CountDownLatch latch = new CountDownLatch(1);
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(message -> latch.countDown());
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test send"));
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry).hasObservationWithNameEqualTo("jms.message.process")
                .that()
                .hasContextualNameEqualTo("test.send process");
        }
    }

    @Test
    void shouldInstrumentMessageListenerWhenException() throws Exception {
        try (Session session = createInstrumentedSession()) {
            Topic topic = session.createTopic("test.send");
            CountDownLatch latch = new CountDownLatch(1);
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(message -> {
                latch.countDown();
                throw new java.lang.IllegalStateException("test error");
            });
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage("test send"));
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry).hasObservationWithNameEqualTo("jms.message.process")
                .that()
                .hasLowCardinalityKeyValue("exception", "IllegalStateException");
        }
    }

    private Session createInstrumentedSession() throws JMSException {
        Session session = jmsConnection.createSession();
        return JmsInstrumentation.instrumentSession(session, registry);
    }

    @AfterEach
    void shutdownServer() throws JMSException {
        jmsConnection.close();
        connectionFactory.close();
        server.stop();
    }

    interface SessionConsumer {

        void accept(Session session) throws JMSException;

    }

    static class NoOpCompletionListener implements CompletionListener {

        @Override
        public void onCompletion(Message message) {

        }

        @Override
        public void onException(Message message, Exception exception) {

        }

    }

}
