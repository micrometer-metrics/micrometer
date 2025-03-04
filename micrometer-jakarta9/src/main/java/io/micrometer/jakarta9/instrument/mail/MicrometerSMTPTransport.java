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
package io.micrometer.jakarta9.instrument.mail;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import jakarta.mail.*;

public class MicrometerSMTPTransport extends Transport {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(MicrometerSMTPTransport.class);

    private static final ObservationConvention<? super MailSendObservationContext> DEFAULT_CONVENTION = new DefaultMailSendObservationConvention();

    @Nullable
    private static ObservationRegistry OBSERVATION_REGISTRY;

    private final Transport delegate;

    private String protocol;

    @Nullable
    private String host;

    private int port;

    public MicrometerSMTPTransport(Session session, URLName urlname) throws NoSuchProviderException {
        super(session, urlname);
        this.protocol = urlname.getProtocol().split(":")[1];
        this.delegate = session.getTransport(this.protocol);
    }

    public static synchronized void install(ObservationRegistry registry) {
        if (OBSERVATION_REGISTRY != null) {
            LOGGER.warn("MicrometerSMTPTransport install called multiple times.");
        }
        OBSERVATION_REGISTRY = registry;
    }

    @Override
    public void connect(String host, int port, String user, String password) throws MessagingException {
        this.delegate.connect(host, port, user, password);
        this.host = host;
        this.port = port;
    }

    @Override
    public void sendMessage(Message msg, Address[] addresses) throws MessagingException {
        if (OBSERVATION_REGISTRY == null) {
            LOGGER.warn("MicrometerSMTPTransport install never called.");
            this.delegate.sendMessage(msg, addresses);
            return;
        }

        Observation observation = MailObservationDocumentation.MAIL_SEND.observation(null, DEFAULT_CONVENTION,
                () -> new MailSendObservationContext(msg, this.protocol, this.host, this.port), OBSERVATION_REGISTRY);
        observation.start();
        try (Observation.Scope scope = observation.openScope()) {
            // the Message-Id is set by the SMTP after sending the message
            this.delegate.sendMessage(msg, addresses);
            observation.highCardinalityKeyValue(MailKeyValues.smtpMessageId(msg));
        }
        catch (MessagingException error) {
            observation.error(error);
            throw error;
        }
        finally {
            observation.stop();
        }
    }

    @Override
    public synchronized void close() throws MessagingException {
        this.delegate.close();
        this.host = null;
        this.port = 0;
    }

}
