/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.jakarta9.instrument.mail;

import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.observation.transport.SenderContext;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import org.jspecify.annotations.Nullable;

/**
 * Context that holds information for observation metadata collection during the
 * {@link MailObservationDocumentation#MAIL_SEND sending of mail messages}.
 * <p>
 * This propagates metadata with the message sent by
 * {@link Message#setHeader(String, String) setting a message header}.
 *
 * @since 1.16.0
 * @author famaridon
 */
public class MailSendObservationContext extends SenderContext<Message> {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(MailSendObservationContext.class);

    private final @Nullable String protocol;

    private final @Nullable String host;

    private final int port;

    public MailSendObservationContext(Message msg, @Nullable String protocol, @Nullable String host, int port) {
        super((message, key, value) -> {
            if (message != null) {
                try {
                    message.setHeader(key, value);
                }
                catch (MessagingException exc) {
                    logger.log("Failed to set message header.", exc);
                }
            }
        });
        setCarrier(msg);
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    public @Nullable String getProtocol() {
        return protocol;
    }

    public @Nullable String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

}
