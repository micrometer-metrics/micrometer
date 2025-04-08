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

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import jakarta.mail.*;

/**
 * Wraps a {@link Transport} so that it is instrumented with a Micrometer
 * {@link Observation}.
 *
 * @since 1.15.0
 * @author famaridon
 */
public class InstrumentedTransport extends Transport {

    private static final DefaultMailSendObservationConvention DEFAULT_CONVENTION = new DefaultMailSendObservationConvention();

    private final ObservationRegistry observationRegistry;

    private final Transport delegate;

    @Nullable
    private final String protocol;

    @Nullable
    private String host;

    @Nullable
    private final ObservationConvention<MailSendObservationContext> customConvention;

    private int port;

    /**
     * Create an instrumented transport using the
     * {@link DefaultMailSendObservationConvention default} {@link ObservationConvention}.
     * @param session session for the delegate transport
     * @param delegate transport to instrument
     * @param observationRegistry registry for the observations
     */
    public InstrumentedTransport(Session session, Transport delegate, ObservationRegistry observationRegistry) {
        this(session, delegate, observationRegistry, null);
    }

    /**
     * Create an instrumented transport with a custom {@link MailSendObservationConvention
     * convention}.
     * @param session session for the delegate transport
     * @param delegate transport to instrument
     * @param observationRegistry registry for the observations
     * @param customConvention override the convention to apply to the instrumentation
     */
    public InstrumentedTransport(Session session, Transport delegate, ObservationRegistry observationRegistry,
            @Nullable ObservationConvention<MailSendObservationContext> customConvention) {
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
        try (Observation.Scope ignore = observation.openScope()) {
            // the Message-Id is set by the Transport (from the SMTP server) after sending
            this.delegate.sendMessage(msg, addresses);
            MailKeyValues.smtpMessageId(msg).ifPresent(observation::highCardinalityKeyValue);
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
