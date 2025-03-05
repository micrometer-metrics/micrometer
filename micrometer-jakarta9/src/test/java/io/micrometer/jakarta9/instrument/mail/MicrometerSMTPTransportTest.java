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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
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

class MicrometerSMTPTransportTest {

    private Session session;

    private MockSMTPTransportListener listener;

    private ObservationRegistry registry;

    private Context lastContext;

    @BeforeEach
    void setUp() {
        this.listener = Mockito.mock(MockSMTPTransportListener.class);
        registry = ObservationRegistry.create();
        this.registry.observationConfig().observationHandler(new ObservationHandler<Context>() {
            @Override
            public boolean supportsContext(Context context) {
                return true;
            }

            @Override
            public void onStop(Context context) {
                lastContext = context;
            }
        });

        // prepare properties for the session
        Properties smtpProperties = new Properties();
        // instrument the transport
        smtpProperties.put("mail.transport.protocol", "micrometer:mocksmtp");

        // listen the mock
        MockSMTPTransport.LISTENER = listener;

        // the class have to open a Session
        this.session = Session.getInstance(smtpProperties);
    }

    @Test
    void shouldConstructDelegatedTransport() throws NoSuchProviderException {
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        assertThat(transport).isInstanceOf(MicrometerSMTPTransport.class);
        verify(listener).onConstructor(this.session, new URLName("mocksmtp:"));
    }

    @Test
    void shouldDelegateConnect() throws MessagingException {
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        transport.connect("host", 123, "user", "password");
        verify(listener).onConnect("host", 123, "user", "password");
    }

    @Test
    void shouldDelegateClose() throws MessagingException {
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        transport.close();
        verify(listener).onClose();
    }

    @Test
    void shouldDelegateSendMessageEvenIfObservationRegistryIsNotInstalled() throws MessagingException {
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        Message msg = new MimeMessage(this.session);
        Address[] to = new Address[0];
        transport.sendMessage(msg, to);
        verify(listener).onSendMessage(msg, to);
    }

    @Test
    void shouldDelegateSendMessageIfObservationRegistryIsInstalled() throws MessagingException {
        MicrometerSMTPTransport.install(this.registry);
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        Message msg = new MimeMessage(this.session);
        Address[] to = new Address[0];
        transport.sendMessage(msg, to);
        verify(listener).onSendMessage(msg, to);
    }

    @Test
    void shouldCreateObservationWhenSendMessageIsCalled() throws MessagingException {
        // arrange
        MicrometerSMTPTransport.install(this.registry);
        when(listener.onSendMessage(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            Message message = (Message) invocation.getArguments()[0];
            message.setHeader("Message-Id", "message-id");
            return true;
        });
        Transport transport = this.session.getTransport("micrometer:mocksmtp");
        Message msg = new MimeMessage(this.session);
        msg.setSubject("Hello world");
        Address[] to = new Address[0];
        msg.setFrom(new InternetAddress("from@example.com"));
        msg.addRecipient(RecipientType.TO, new InternetAddress("to@example.com"));
        transport.connect("example.com", 123, "user", "password");

        // act
        transport.sendMessage(msg, to);

        // assert
        assertThat(lastContext).isNotNull();
        assertThat(lastContext.getHighCardinalityKeyValues()).contains(KeyValue.of("smtp.message.id", "message-id"),
                KeyValue.of("smtp.message.subject", "Hello world"), KeyValue.of("smtp.message.to", "to@example.com"),
                KeyValue.of("smtp.message.from", "from@example.com"));

        assertThat(lastContext.getLowCardinalityKeyValues()).contains(KeyValue.of("network.protocol.name", "mocksmtp"),
                KeyValue.of("server.address", "example.com"), KeyValue.of("server.port", "123"));
    }

}
