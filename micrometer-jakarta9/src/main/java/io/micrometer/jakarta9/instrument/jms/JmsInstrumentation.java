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

import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.Session;

import java.lang.reflect.Proxy;

/**
 * Instrument Jakarta JMS {@link Session sessions} for observability.
 * <p>
 * This instrumentation {@link Proxy proxies} JMS sessions to intercept various calls and
 * create dedicated observations:
 * <ul>
 * <li>{@code send*} method calls on {@link jakarta.jms.MessageProducer} will create
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PUBLISH "jms.message.publish"}
 * observations.
 * <li>When configuring a {@link jakarta.jms.MessageListener} on
 * {@link jakarta.jms.MessageConsumer} instances returned by the session,
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PROCESS "jms.message.process"}
 * observations are created when messages are received by the callback.
 * </ul>
 * <p>
 * Here is how an existing JMS Session instance can be instrumented for observability:
 * <pre>
 * Session original = ...
 * ObservationRegistry registry = ...
 * Session session = JmsInstrumentation.instrumentSession(original, registry);
 *
 * Topic topic = session.createTopic("micrometer.test.topic");
 * MessageProducer producer = session.createProducer(topic);
 * // this operation will create a "jms.message.publish" observation
 * producer.send(session.createMessage("test message content"));
 *
 * MessageConsumer consumer = session.createConsumer(topic);
 * // when a message is processed by the listener,
 * // a "jms.message.process" observation is created
 * consumer.setMessageListener(message -> consumeMessage(message));
 * </pre>
 *
 * @author Brian Clozel
 * @since 1.12.0
 * @see JmsObservationDocumentation
 */
public abstract class JmsInstrumentation {

    private JmsInstrumentation() {

    }

    /**
     * Instrument the {@link Session} given as argument for observability and record
     * observations using the provided Observation registry.
     * @param session the target session to proxy for instrumentation
     * @param registry the Observation registry to use
     * @return the instrumented session that should be used to record observations
     */
    public static Session instrumentSession(Session session, ObservationRegistry registry) {
        SessionInvocationHandler handler = new SessionInvocationHandler(session, registry);
        return (Session) Proxy.newProxyInstance(session.getClass().getClassLoader(), new Class[] { Session.class },
                handler);
    }

}
