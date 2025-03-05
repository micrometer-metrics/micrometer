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
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMailSendObservationConventionTests {

    private final DefaultMailSendObservationConvention convention = new DefaultMailSendObservationConvention();

    @Test
    void shouldHaveObservationName() {
        assertThat(convention.getName()).isEqualTo("mail.send");
    }

    @Test
    void shouldHaveContextualName() {
        MailSendObservationContext context = new MailSendObservationContext(new MimeMessage((Session) null), "smtp",
                "localhost", 25);
        assertThat(convention.getContextualName(context)).isEqualTo("mail send");
    }

    @Test
    void shouldHaveHighCardinalityKeyValues() throws MessagingException {
        MimeMessage message = new MimeMessage((Session) null);
        message.setFrom(new InternetAddress("from@example.com"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("to@example.com"));
        message.setSubject("Test Subject");

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(convention.getHighCardinalityKeyValues(context)).contains(
                KeyValue.of("smtp.message.from", "from@example.com"), KeyValue.of("smtp.message.to", "to@example.com"),
                KeyValue.of("smtp.message.subject", "Test Subject"));
    }

    @Test
    void shouldHaveLowCardinalityKeyValues() throws MessagingException {
        MimeMessage message = new MimeMessage((Session) null);
        message.setFrom(new InternetAddress("from@example.com"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("to@example.com"));
        message.setSubject("Test Subject");

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "example.com", 587);
        assertThat(convention.getLowCardinalityKeyValues(context)).contains(
                KeyValue.of("server.address", "example.com"), KeyValue.of("server.port", "587"),
                KeyValue.of("network.protocol.name", "smtp"));
    }

    @Test
    void shouldHandleMessagingException() throws MessagingException {
        MimeMessage message = new MimeMessage((Session) null) {
            @Override
            public Address[] getFrom() throws MessagingException {
                throw new MessagingException("test exception");
            }
        };

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(convention.getHighCardinalityKeyValues(context)).contains(
                KeyValue.of("smtp.message.from", "unknown"), KeyValue.of("smtp.message.to", "unknown"),
                KeyValue.of("smtp.message.subject", "unknown"));
    }

    @Test
    void shouldHandleMultipleFromAddresses() throws MessagingException {
        MimeMessage message = new MimeMessage((Session) null);
        message.setFrom(new InternetAddress("from1@example.com"));
        message.addFrom(new InternetAddress[] { new InternetAddress("from2@example.com") });

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("smtp.message.from", "from1@example.com, from2@example.com"));
    }

    @Test
    void shouldHandleMultipleToAddresses() throws MessagingException {
        MimeMessage message = new MimeMessage((Session) null);
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("to1@example.com"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("to2@example.com"));

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("smtp.message.to", "to1@example.com, to2@example.com"));
    }

}
