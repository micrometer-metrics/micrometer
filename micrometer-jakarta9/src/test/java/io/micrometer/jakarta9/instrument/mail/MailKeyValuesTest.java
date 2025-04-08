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
 *
 */
package io.micrometer.jakarta9.instrument.mail;

import static io.micrometer.jakarta9.instrument.mail.MailKeyValues.UNKNOWN;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_FROM;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_SUBJECT;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests for {@link MailKeyValues}.
 *
 * @author famaridon
 */
class MailKeyValuesTest {

    @Test
    @DisplayName("Should return UNKNOWN when 'from' address retrieval fails")
    void smtpMessageFrom_WhenFromFails_ReturnsUnknown() throws MessagingException {
        var message = Mockito.mock(Message.class);
        Mockito.when(message.getFrom()).thenThrow(new MessagingException("test exception"));
        var result = MailKeyValues.smtpMessageFrom(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_FROM.withValue(UNKNOWN));
    }

    @Test
    @DisplayName("Should return EMPTY when 'from' address is unset")
    void smtpMessageFrom_WhenFromUnset_ReturnsNone() {
        var message = new MimeMessage((Session) null);
        var result = MailKeyValues.smtpMessageFrom(message);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return EMPTY when 'from' address is empty")
    void smtpMessageFrom_WhenFromEmpty_ReturnsNone() throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[0]);
        var result = MailKeyValues.smtpMessageFrom(message);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return single 'from' address when one is set")
    void smtpMessageFrom_WhenOneFromAddress_ReturnsAddress() throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[] { new InternetAddress("test@exemple.com") });
        var result = MailKeyValues.smtpMessageFrom(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_FROM.withValue("test@exemple.com"));
    }

    @Test
    @DisplayName("Should return multiple 'from' addresses when more than one is set")
    void smtpMessageFrom_WhenMultipleFromAddresses_ReturnsAddresses() throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.addFrom(new InternetAddress[] { new InternetAddress("test@exemple.com"),
                new InternetAddress("other@example.com") });
        var result = MailKeyValues.smtpMessageFrom(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_FROM.withValue("test@exemple.com, other@example.com"));
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    @DisplayName("Should return EMPTY when recipients are unset")
    void smtpMessageRecipients_WhenRecipientsUnset_ReturnsNone(RecipientType recipientType) {
        var message = new MimeMessage((Session) null);
        var result = MailKeyValues.smtpMessageRecipients(message, recipientType);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    @DisplayName("Should return UNKNOWN when recipient retrieval fails")
    void smtpMessageRecipients_WhenRecipientsFail_ReturnsUnknown(RecipientType recipientType)
            throws MessagingException {
        MailObservationDocumentation.HighCardinalityKeyNames key = MailObservationDocumentation.HighCardinalityKeyNames
            .valueOf("SMTP_MESSAGE_" + recipientType.toString().toUpperCase(Locale.ROOT));
        var message = Mockito.mock(Message.class);
        Mockito.when(message.getRecipients(ArgumentMatchers.any())).thenThrow(new MessagingException("test exception"));
        var result = MailKeyValues.smtpMessageRecipients(message, recipientType);
        assertThat(result).isPresent().hasValue(key.withValue(UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    @DisplayName("Should return EMPTY when recipients are empty")
    void smtpMessageRecipients_WhenRecipientsEmpty_ReturnsNone(RecipientType recipientType) throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[0]);
        var result = MailKeyValues.smtpMessageRecipients(message, recipientType);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    @DisplayName("Should return single recipient when one is set")
    void smtpMessageRecipients_WhenOneRecipient_ReturnsRecipient(RecipientType recipientType)
            throws MessagingException {
        MailObservationDocumentation.HighCardinalityKeyNames key = MailObservationDocumentation.HighCardinalityKeyNames
            .valueOf("SMTP_MESSAGE_" + recipientType.toString().toUpperCase(Locale.ROOT));
        var message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[] { new InternetAddress("test@exemple.com") });
        var result = MailKeyValues.smtpMessageRecipients(message, recipientType);
        assertThat(result).isPresent().hasValue(key.withValue("test@exemple.com"));
    }

    @ParameterizedTest
    @MethodSource("recipientTypes")
    @DisplayName("Should return multiple recipients when more than one is set")
    void smtpMessageRecipients_WhenMultipleRecipients_ReturnsRecipients(RecipientType recipientType)
            throws MessagingException {
        MailObservationDocumentation.HighCardinalityKeyNames key = MailObservationDocumentation.HighCardinalityKeyNames
            .valueOf("SMTP_MESSAGE_" + recipientType.toString().toUpperCase(Locale.ROOT));
        var message = new MimeMessage((Session) null);
        message.addRecipients(recipientType, new InternetAddress[] { new InternetAddress("test@exemple.com"),
                new InternetAddress("other@example.com") });
        var result = MailKeyValues.smtpMessageRecipients(message, recipientType);

        assertThat(result).isPresent().hasValue(key.withValue("test@exemple.com, other@example.com"));
    }

    public static Stream<Arguments> recipientTypes() {
        return Stream.of(Arguments.of(Message.RecipientType.TO), Arguments.of(Message.RecipientType.CC),
                Arguments.of(Message.RecipientType.BCC));
    }

    @Test
    @DisplayName("Should return subject when it is set")
    void smtpMessageSubject_WhenSubjectSet_ReturnsSubject() throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.setSubject("test subject");
        var result = MailKeyValues.smtpMessageSubject(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_SUBJECT.withValue("test subject"));
    }

    @Test
    @DisplayName("Should return UNKNOWN when subject retrieval fails")
    void smtpMessageSubject_WhenSubjectFails_ReturnsUnknown() throws MessagingException {
        var message = Mockito.mock(Message.class);
        Mockito.when(message.getSubject()).thenThrow(new MessagingException("test exception"));
        var result = MailKeyValues.smtpMessageSubject(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_SUBJECT.withValue(UNKNOWN));
    }

    @Test
    @DisplayName("Should return EMPTY when subject is unset")
    void smtpMessageSubject_WhenSubjectUnset_ReturnsNone() {
        var message = new MimeMessage((Session) null);
        var result = MailKeyValues.smtpMessageSubject(message);
        assertThat(result).isEmpty();

    }

    @Test
    @DisplayName("Should return server address when it is provided")
    void serverAddress_WhenProvided_ReturnsAddress() {
        var msg = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(msg, "smtp", "localhost", 25);

        var result = MailKeyValues.serverAddress(ctx);
        assertThat(result.getValue()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("Should return UNKNOWN when server address is not provided")
    void serverAddress_WhenNotProvided_ReturnsUnknown() {
        var msg = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(msg, "smtp", null, 25);

        var result = MailKeyValues.serverAddress(ctx);
        assertThat(result.getValue()).isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("Should return server port when it is valid")
    void serverPort_WhenValid_ReturnsPort() {
        var msg = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(msg, "smtp", "localhost", 25);

        var result = MailKeyValues.serverPort(ctx);
        assertThat(result.getValue()).isEqualTo("25");
    }

    @Test
    @DisplayName("Should return UNKNOWN when server port is zero or negative")
    void serverPort_WhenZeroOrNegative_ReturnsUnknown() {
        var msg = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(msg, "smtp", "localhost", 0);

        var result = MailKeyValues.serverPort(ctx);
        assertThat(result.getValue()).isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("Should return UNKNOWN when Message-ID header retrieval fails")
    void smtpMessageId_WhenHeaderFails_ReturnsUnknown() throws MessagingException {
        var message = Mockito.mock(Message.class);
        Mockito.when(message.getHeader("Message-ID")).thenThrow(new MessagingException("test exception"));
        var result = MailKeyValues.smtpMessageId(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_ID.withValue(UNKNOWN));
    }

    @Test
    @DisplayName("Should return EMPTY when Message-ID header is missing")
    void smtpMessageId_WhenHeaderMissing_ReturnsUnknown() {
        var message = new MimeMessage((Session) null);
        var result = MailKeyValues.smtpMessageId(message);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return Message-ID when it is set")
    void smtpMessageId_WhenHeaderSet_ReturnsMessageId() throws MessagingException {
        var message = new MimeMessage((Session) null);
        message.addHeader("Message-ID", "12345@example.com");
        var result = MailKeyValues.smtpMessageId(message);
        assertThat(result).isPresent().hasValue(SMTP_MESSAGE_ID.withValue("12345@example.com"));
    }

    @Test
    @DisplayName("Should return network protocol name when it is provided")
    void networkProtocolName() {
        var message = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(message, "smtp", "localhost", 25);
        var result = MailKeyValues.networkProtocolName(ctx);
        assertThat(result.getValue()).isEqualTo("smtp");
    }

    @Test
    @DisplayName("Should return UNKNOWN when network protocol name is null")
    void networkProtocolName_WhenNull() {
        var message = new MimeMessage((Session) null);
        var ctx = new MailSendObservationContext(message, null, "localhost", 25);
        var result = MailKeyValues.networkProtocolName(ctx);
        assertThat(result.getValue()).isEqualTo(UNKNOWN);
    }

}
