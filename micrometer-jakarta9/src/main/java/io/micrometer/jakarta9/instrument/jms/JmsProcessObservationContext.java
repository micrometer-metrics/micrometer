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

import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.observation.transport.ReceiverContext;
import jakarta.jms.Message;

/**
 * Context that holds information for observation metadata collection during the
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PROCESS processing of received JMS
 * messages}.
 * <p>
 * The inbound tracing information is propagated by looking it up in
 * {@link Message#getStringProperty(String) incoming JMS message headers}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class JmsProcessObservationContext extends ReceiverContext<Message> {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(JmsProcessObservationContext.class);

    public JmsProcessObservationContext(Message receivedMessage) {
        super((message, key) -> {
            try {
                return message.getStringProperty(key);
            }
            // Some JMS providers throw exceptions other than JMSException
            catch (Exception exc) {
                logger.log("Failed to get message property.", exc);
                return null;
            }
        });
        setCarrier(receivedMessage);
    }

}
