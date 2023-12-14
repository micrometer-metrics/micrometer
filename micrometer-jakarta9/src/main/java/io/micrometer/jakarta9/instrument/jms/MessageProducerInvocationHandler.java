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

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link InvocationHandler} used for proxying Jakarta {@link MessageProducer}.
 * <p>
 * Each invocation to a {@code send*} method is intercepted and a
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PUBLISH dedicated observation} is
 * created. This instrumentation also creates an {@link Observation.Scope} to propagate
 * the observation context and tracing information to the message broker.
 *
 * @author Brian Clozel
 */
class MessageProducerInvocationHandler implements InvocationHandler {

    private static final JmsPublishObservationConvention DEFAULT_CONVENTION = new DefaultJmsPublishObservationConvention();

    private final MessageProducer target;

    private final ObservationRegistry registry;

    MessageProducerInvocationHandler(MessageProducer target, ObservationRegistry registry) {
        this.target = target;
        this.registry = registry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("send".equals(method.getName()) && args[0] != null) {
            Message message = findMessageArgument(args);
            Observation observation = JmsObservationDocumentation.JMS_MESSAGE_PUBLISH.observation(null,
                    DEFAULT_CONVENTION, () -> new JmsPublishObservationContext(message), this.registry);
            observation.start();
            try (Observation.Scope scope = observation.openScope()) {
                return method.invoke(this.target, args);
            }
            catch (InvocationTargetException exc) {
                observation.error(exc.getTargetException());
                throw exc.getTargetException();
            }
            catch (Throwable error) {
                observation.error(error);
                throw error;
            }
            finally {
                observation.stop();
            }
        }
        try {
            return method.invoke(this.target, args);
        }
        catch (InvocationTargetException exc) {
            throw exc.getTargetException();
        }
    }

    @Nullable
    private Message findMessageArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Message) {
                return (Message) arg;
            }
        }
        return null;
    }

}
