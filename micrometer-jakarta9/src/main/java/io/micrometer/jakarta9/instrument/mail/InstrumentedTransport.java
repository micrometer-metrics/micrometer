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

public class InstrumentedTransport extends Transport {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(InstrumentedTransport.class);

    private static final ObservationConvention<MailSendObservationContext> DEFAULT_CONVENTION = new DefaultMailSendObservationConvention();

    private ObservationRegistry observationRegistry;

    private final Transport delegate;

    private final String protocol;

    @Nullable
    private String host;

    @Nullable
    private final ObservationConvention<MailSendObservationContext> customConvention;

    private int port;

    public InstrumentedTransport(Session session, Transport delegate, ObservationRegistry observationRegistry)
            throws NoSuchProviderException {
        this(session, delegate, observationRegistry, null);
    }

    public InstrumentedTransport(Session session, Transport delegate, ObservationRegistry observationRegistry,
            @Nullable ObservationConvention<MailSendObservationContext> customConvention)
            throws NoSuchProviderException {
        super(session, delegate.getURLName());
        this.protocol = this.url.getProtocol();
        this.delegate = delegate;
        this.observationRegistry = observationRegistry;
        this.customConvention = customConvention;
    }

    @Override
    public void connect(String host, int port, String user, String password) throws MessagingException {
        this.delegate.connect(host, port, user, password);
        this.host = host;
        this.port = port;
    }

    @Override
    public void sendMessage(Message msg, Address[] addresses) throws MessagingException {

        Observation observation = MailObservationDocumentation.MAIL_SEND.observation(this.customConvention,
                DEFAULT_CONVENTION, () -> new MailSendObservationContext(msg, this.protocol, this.host, this.port),
                observationRegistry);

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
