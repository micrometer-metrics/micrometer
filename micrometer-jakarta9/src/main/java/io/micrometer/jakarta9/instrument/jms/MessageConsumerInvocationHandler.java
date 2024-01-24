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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link InvocationHandler} used for proxying Jakarta {@link MessageConsumer}.
 * <p>
 * Each invocation to a {@code setMessageListener} method is intercepted and the given
 * {@link MessageListener} is wrapped to create a
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PROCESS dedicated observation}. This
 * instrumentation also creates an {@link Observation.Scope} to propagate the observation
 * context in the listener callback.
 *
 * @author Brian Clozel
 */
class MessageConsumerInvocationHandler implements InvocationHandler {

    private static final JmsProcessObservationConvention DEFAULT_CONVENTION = new DefaultJmsProcessObservationConvention();

    private final MessageConsumer target;

    private final ObservationRegistry registry;

    MessageConsumerInvocationHandler(MessageConsumer target, ObservationRegistry registry) {
        this.target = target;
        this.registry = registry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if ("setMessageListener".equals(method.getName()) && args[0] != null) {
                MessageListener listener = (MessageListener) args[0];
                return method.invoke(this.target, new ObservedMessageListener(listener, this.registry));
            }
            return method.invoke(this.target, args);
        }
        catch (InvocationTargetException exc) {
            throw exc.getTargetException();
        }
    }

    static class ObservedMessageListener implements MessageListener {

        private final MessageListener delegate;

        private final ObservationRegistry registry;

        ObservedMessageListener(MessageListener delegate, ObservationRegistry registry) {
            this.delegate = delegate;
            this.registry = registry;
        }

        @Override
        public void onMessage(Message message) {
            Observation observation = JmsObservationDocumentation.JMS_MESSAGE_PROCESS.observation(null,
                    DEFAULT_CONVENTION, () -> new JmsProcessObservationContext(message), this.registry);
            observation.observe(() -> {
                delegate.onMessage(message);
            });
        }

    }

}
