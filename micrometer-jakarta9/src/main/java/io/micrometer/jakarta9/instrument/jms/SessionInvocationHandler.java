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
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * {@link InvocationHandler} used for proxying Jakarta {@link Session}.
 * <p>
 * Its main purpose is to instrument {@link MessageProducer} and {@link MessageConsumer}
 * instances created by the proxied session.
 *
 * @author Brian Clozel
 */
class SessionInvocationHandler implements InvocationHandler {

    private final Session target;

    private final ObservationRegistry registry;

    private final @Nullable JmsPublishObservationConvention customPublishConvention;

    private final @Nullable JmsProcessObservationConvention customProcessConvention;

    /**
     * Create an invocation handler to be used for proxying a {@link Session}.
     * @param session the proxied session
     * @param registry the observation registry used for recording observations
     * @param customPublishConvention the custom convention to apply to publish
     * observations, or {@code null} to use the default one
     * @param customProcessConvention the custom convention to apply to process
     * observations, or {@code null} to use the default one
     */
    SessionInvocationHandler(Session session, ObservationRegistry registry,
            @Nullable JmsPublishObservationConvention customPublishConvention,
            @Nullable JmsProcessObservationConvention customProcessConvention) {
        this.target = session;
        this.registry = registry;
        this.customPublishConvention = customPublishConvention;
        this.customProcessConvention = customProcessConvention;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            Object result = method.invoke(this.target, args);
            if (result instanceof MessageProducer) {
                MessageProducer producer = (MessageProducer) result;
                MessageProducerInvocationHandler producerHandler = new MessageProducerInvocationHandler(producer,
                        this.registry, this.customPublishConvention);
                return Proxy.newProxyInstance(this.target.getClass().getClassLoader(),
                        new Class[] { MessageProducer.class }, producerHandler);
            }
            if (result instanceof MessageConsumer) {
                MessageConsumer consumer = (MessageConsumer) result;
                MessageConsumerInvocationHandler consumerHandler = new MessageConsumerInvocationHandler(consumer,
                        this.registry, this.customProcessConvention);
                return Proxy.newProxyInstance(this.target.getClass().getClassLoader(),
                        new Class[] { MessageConsumer.class }, consumerHandler);
            }
            return result;
        }
        catch (InvocationTargetException exc) {
            throw exc.getTargetException();
        }
    }

}
