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

import io.micrometer.observation.tck.TestObservationRegistry;
import jakarta.mail.*;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InstrumentedTransport}.
 *
 * @author famaridon
 */
class InstrumentedTransportTest {

    Session session;

    MockSMTPTransportListener listener = Mockito.mock(MockSMTPTransportListener.class);

    TestObservationRegistry registry = TestObservationRegistry.create();

    Transport transport;

    @BeforeEach
    void setUp() throws NoSuchProviderException {
        // prepare properties for the session
        Properties smtpProperties = new Properties();
        // default use of mock to simplify test
        smtpProperties.put("mail.transport.protocol", "mocksmtp");

        // avoid NPE
        MockSMTPTransport.LISTENER = listener;

        // open a Session
        this.session = Session.getInstance(smtpProperties);
        transport = new InstrumentedTransport(session, this.session.getTransport("mocksmtp"), this.registry);
    }

    @Test
    void shouldDelegateConnect() throws MessagingException {
        transport.connect("host", 123, "user", "password");
        verify(listener).onConnect("host", 123, "user", "password");
    }

    @Test
    void shouldDelegateClose() throws MessagingException {
        transport.close();
        verify(listener).onClose();
    }

    @Test
    void shouldDelegateSendMessageEvenIfObservationRegistryIsNotInstalled() throws MessagingException {
        Message msg = new MimeMessage(this.session);
        Address[] to = new Address[0];
        transport.sendMessage(msg, to);
        verify(listener).onSendMessage(msg, to);
    }

    @Test
    void shouldDelegateSendMessageIfObservationRegistryIsInstalled() throws MessagingException {
        Message msg = new MimeMessage(this.session);
        Address[] to = new Address[0];
        transport.sendMessage(msg, to);
        verify(listener).onSendMessage(msg, to);
    }

    @Test
    void shouldCreateObservationWhenSendMessageIsCalled() throws MessagingException {
        // arrange
        when(listener.onSendMessage(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            message.setHeader("Message-Id", "message-id");
            return true;
        });
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
            .hasHighCardinalityKeyValue("smtp.message.id", "message-id")
            .hasHighCardinalityKeyValue("smtp.message.subject", "Hello world")
            .hasHighCardinalityKeyValue("smtp.message.to", "to@example.com")
            .hasHighCardinalityKeyValue("smtp.message.from", "from@example.com")
            .hasLowCardinalityKeyValue("server.address", "example.com")
            .hasLowCardinalityKeyValue("server.port", "123")
            .hasLowCardinalityKeyValue("network.protocol.name", "mocksmtp");
    }

}
