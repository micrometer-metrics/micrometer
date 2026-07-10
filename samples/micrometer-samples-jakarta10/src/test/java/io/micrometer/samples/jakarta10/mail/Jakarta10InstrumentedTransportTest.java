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
package io.micrometer.samples.jakarta10.mail;

import io.micrometer.jakarta9.instrument.mail.InstrumentedTransport;
import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.mail.*;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InstrumentedTransport} on Jakarta 10 Mail dependencies.
 */
class Jakarta10InstrumentedTransportTest {

    Session session;

    TestObservationRegistry registry = TestObservationRegistry.create();

    Transport transport;

    @BeforeEach
    void setUp() throws NoSuchProviderException {
        // prepare properties for the session
        Properties smtpProperties = new Properties();
        // default use of mock to simplify test
        smtpProperties.put("mail.transport.protocol", "mocksmtp");

        // open a Session
        this.session = Session.getInstance(smtpProperties);
        transport = new InstrumentedTransport(session, this.session.getTransport("mocksmtp"), this.registry);
    }

    @Test
    void shouldCreateObservationWhenSendMessageIsCalled() throws MessagingException {
        // arrange
        Message msg = new MimeMessage(this.session);
        msg.setSubject("Hello world");
        Address[] to = new Address[0];
        msg.setFrom(new InternetAddress("from@example.com"));
        msg.addRecipient(RecipientType.TO, new InternetAddress("to@example.com"));
        transport.connect("example.com", 123, "user", "password");

        // act
        transport.sendMessage(msg, to);

        // assert
        assertThat(registry).hasObservationWithNameEqualTo("mail.send")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasHighCardinalityKeyValue("smtp.message.subject", "Hello world")
            .hasHighCardinalityKeyValue("smtp.message.to", "to@example.com")
            .hasHighCardinalityKeyValue("smtp.message.from", "from@example.com")
            .hasLowCardinalityKeyValue("server.address", "example.com")
            .hasLowCardinalityKeyValue("server.port", "123")
            .hasLowCardinalityKeyValue("network.protocol.name", "mocksmtp");
    }

}
