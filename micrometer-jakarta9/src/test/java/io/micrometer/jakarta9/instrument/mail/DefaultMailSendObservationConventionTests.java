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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.*;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.LowCardinalityKeyNames.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMailSendObservationConvention}.
 *
 * @author famaridon
 */
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
    void shouldHandleMessagingException() {
        MimeMessage message = new MimeMessage((Session) null) {
            @Override
            public Address[] getFrom() throws MessagingException {
                throw new MessagingException("test exception");
            }
        };

        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(convention.getHighCardinalityKeyValues(context))
            .contains(KeyValue.of("smtp.message.from", "unknown"));
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

    @Test
    void fromShouldBeUnknownWhenFetchingFails() throws MessagingException {
        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getFrom()).thenThrow(new MessagingException("test exception"));
        assertThat(getHighCardinalityKeyValues(message)).containsExactly(SMTP_MESSAGE_FROM.withValue("unknown"));
    }

    @Test
    void fieldsShouldBeMissingWhenUnset() {
        Message message = new MimeMessage((Session) null);
        assertThat(getHighCardinalityKeyValues(message)).isEmpty();
    }

    @Test
    void fromShouldBeMissingWhenEmpty() throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[0]);
        assertThat(getHighCardinalityKeyValues(message)).isEmpty();
    }

    @Test
    void fromShouldBeThereWhenSet() throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[] { new InternetAddress("test@exemple.com") });
        assertThat(getHighCardinalityKeyValues(message))
            .containsExactly(SMTP_MESSAGE_FROM.withValue("test@exemple.com"));
    }

    @Test
    void multipleFromShouldBeThereWhenMultipleSet() throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[] { new InternetAddress("test@exemple.com"),
                new InternetAddress("other@example.com") });
        assertThat(getHighCardinalityKeyValues(message))
            .containsExactly(SMTP_MESSAGE_FROM.withValue("test@exemple.com, other@example.com"));
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    void recipientsShouldBeUnknownWhenFetchingFails(Message.RecipientType recipientType) throws MessagingException {
        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getRecipients(recipientType)).thenThrow(new MessagingException("test exception"));
        KeyValue keyValue = KeyValue.of("smtp.message." + String.valueOf(recipientType).toLowerCase(Locale.ROOT),
                "unknown");
        assertThat(getHighCardinalityKeyValues(message)).containsExactly(keyValue);
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    void recipientsShouldBeMissingWhenUnset(Message.RecipientType recipientType) throws MessagingException {
        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getRecipients(recipientType)).thenReturn(null);
        assertThat(getHighCardinalityKeyValues(message)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    void recipientsShouldBeMissingWhenEmpty(Message.RecipientType recipientType) throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[0]);
        assertThat(getHighCardinalityKeyValues(message)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    void recipientsShouldBeThereWhenSet(Message.RecipientType recipientType) throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[] { new InternetAddress("test@exemple.com") });
        KeyValue keyValue = KeyValue.of("smtp.message." + String.valueOf(recipientType).toLowerCase(Locale.ROOT),
                "test@exemple.com");
        assertThat(getHighCardinalityKeyValues(message)).containsExactly(keyValue);
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    void multipleRecipientsShouldBeThereWhenMultipleSet(Message.RecipientType recipientType) throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[] { new InternetAddress("test@exemple.com"),
                new InternetAddress("other@example.com") });
        KeyValue keyValue = KeyValue.of("smtp.message." + String.valueOf(recipientType).toLowerCase(Locale.ROOT),
                "test@exemple.com, other@example.com");
        assertThat(getHighCardinalityKeyValues(message)).containsExactly(keyValue);
    }

    @Test
    void subjectShouldBeUnknownWhenFetchingFails() throws MessagingException {
        var message = Mockito.mock(Message.class);
        Mockito.when(message.getSubject()).thenThrow(new MessagingException("test exception"));
        assertThat(getHighCardinalityKeyValues(message)).containsExactly(SMTP_MESSAGE_SUBJECT.withValue("unknown"));
    }

    @Test
    void subjectShouldBeThereWhenSet() throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.setSubject("test subject");
        assertThat(getHighCardinalityKeyValues(message))
            .containsExactly(SMTP_MESSAGE_SUBJECT.withValue("test subject"));
    }

    @Test
    void protocolAddressAndPortShouldBeThereWhenSet() {
        Message message = new MimeMessage((Session) null);
        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        assertThat(getLowCardinalityKeyValues(context)).containsExactly(NETWORK_PROTOCOL_NAME.withValue("smtp"),
                SERVER_ADDRESS.withValue("localhost"), SERVER_PORT.withValue("25"));
    }

    @Test
    void protocolAndAddressShouldBeUnknownWhenUnsetOrInvalid() {
        Message message = new MimeMessage((Session) null);
        MailSendObservationContext context = new MailSendObservationContext(message, null, null, 0);
        assertThat(getLowCardinalityKeyValues(context)).containsExactly(NETWORK_PROTOCOL_NAME.withValue("unknown"),
                SERVER_ADDRESS.withValue("unknown"), SERVER_PORT.withValue("unknown"));
    }

    @Test
    void messageIdShouldBeUnknownWhenFetchingFails() throws MessagingException {
        Message message = Mockito.mock(Message.class);
        Mockito.when(message.getHeader("Message-ID")).thenThrow(new MessagingException("test exception"));
        assertThat(convention.smtpMessageId(message)).isPresent().hasValue(SMTP_MESSAGE_ID.withValue("unknown"));
    }

    @Test
    void messageIdShouldBeMissingWhenEmpty() {
        Message message = new MimeMessage((Session) null);
        assertThat(convention.smtpMessageId(message)).isEmpty();
    }

    @Test
    void messageIdShouldBeThereWhenSet() throws MessagingException {
        Message message = new MimeMessage((Session) null);
        message.addHeader("Message-ID", "12345@example.com");
        assertThat(convention.smtpMessageId(message)).isPresent()
            .hasValue(SMTP_MESSAGE_ID.withValue("12345@example.com"));
    }

    private List<KeyValue> getHighCardinalityKeyValues(Message message) {
        MailSendObservationContext context = new MailSendObservationContext(message, "smtp", "localhost", 25);
        KeyValues highCardinalityKeyValues = convention.getHighCardinalityKeyValues(context);
        return highCardinalityKeyValues.stream().collect(Collectors.toList());
    }

    private List<KeyValue> getLowCardinalityKeyValues(MailSendObservationContext context) {
        KeyValues lowCardinalityKeyValues = convention.getLowCardinalityKeyValues(context);
        return lowCardinalityKeyValues.stream().collect(Collectors.toList());
    }

    static Stream<Arguments> recipientTypes() {
        return Stream.of(Arguments.of(Message.RecipientType.TO), Arguments.of(Message.RecipientType.CC),
                Arguments.of(Message.RecipientType.BCC));
    }

}
