/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.binder.jms;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.transport.SenderContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

/**
 * Context that holds information for observation metadata collection during the
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PUBLISH publication of JMS messages}.
 * <p>
 * This propagates the tracing information with the message sent by
 * {@link Message#setStringProperty(String, String) setting a message header}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class JmsPublishObservationContext extends SenderContext<Message> {

    public JmsPublishObservationContext(@Nullable Message sendMessage) {
        super((message, key, value) -> {
            try {
                if (message != null) {
                    message.setStringProperty(key, value);
                }
            }
            catch (JMSException exc) {
                // ignore
            }
        });
        setCarrier(sendMessage);
    }

}
