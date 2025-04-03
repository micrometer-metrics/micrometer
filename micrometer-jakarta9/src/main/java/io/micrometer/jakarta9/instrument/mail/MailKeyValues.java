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

import io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.LowCardinalityKeyNames;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.micrometer.common.KeyValue;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Message.RecipientType;

class MailKeyValues {

    private static final KeyValue SMTP_MESSAGE_FROM_UNKNOWN = KeyValue
        .of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_FROM, "unknown");

    private static final KeyValue SMTP_MESSAGE_TO_UNKNOWN = KeyValue
        .of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_TO, "unknown");

    private static final KeyValue SMTP_MESSAGE_SUBJECT_UNKNOWN = KeyValue
        .of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_SUBJECT, "unknown");

    private static final KeyValue SMTP_MESSAGE_ID_UNKNOWN = KeyValue
        .of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_ID, "unknown");

    private static final KeyValue SERVER_ADDRESS_UNKNOWN = KeyValue.of(LowCardinalityKeyNames.SERVER_ADDRESS,
            "unknown");

    private static final KeyValue SERVER_PORT_UNKNOWN = KeyValue.of(LowCardinalityKeyNames.SERVER_PORT, "unknown");

    private MailKeyValues() {
    }

    static KeyValue smtpMessageFrom(Message message) {
        try {
            if (message.getFrom() == null || message.getFrom().length == 0) {
                return SMTP_MESSAGE_FROM_UNKNOWN;
            }
            String fromString = Arrays.stream(message.getFrom())
                .map(Address::toString)
                .collect(Collectors.joining(", "));
            return KeyValue.of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_FROM, fromString);
        }
        catch (MessagingException exc) {
            return SMTP_MESSAGE_FROM_UNKNOWN;
        }
    }

    static KeyValue smtpMessageTo(Message message) {
        try {
            Address[] recipients = message.getRecipients(RecipientType.TO);
            if (recipients == null || recipients.length == 0) {
                return SMTP_MESSAGE_TO_UNKNOWN;
            }
            String recipientsString = Arrays.stream(recipients)
                .map(Address::toString)
                .collect(Collectors.joining(", "));
            return KeyValue.of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_TO, recipientsString);
        }
        catch (MessagingException exc) {
            return SMTP_MESSAGE_TO_UNKNOWN;
        }
    }

    static KeyValue smtpMessageSubject(Message message) {
        try {
            if (message.getSubject() == null) {
                return SMTP_MESSAGE_SUBJECT_UNKNOWN;
            }
            return KeyValue.of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_SUBJECT,
                    message.getSubject());
        }
        catch (MessagingException exc) {
            return SMTP_MESSAGE_SUBJECT_UNKNOWN;
        }
    }

    static KeyValue smtpMessageId(Message message) {
        try {
            if (message.getHeader("Message-ID") == null) {
                return SMTP_MESSAGE_ID_UNKNOWN;
            }
            return KeyValue.of(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_ID,
                    message.getHeader("Message-ID")[0]);
        }
        catch (MessagingException exc) {
            return SMTP_MESSAGE_ID_UNKNOWN;
        }
    }

    static KeyValue serverAddress(MailSendObservationContext context) {
        if (context.getHost() == null) {
            return SERVER_ADDRESS_UNKNOWN;
        }
        return KeyValue.of(LowCardinalityKeyNames.SERVER_ADDRESS, context.getHost());
    }

    static KeyValue serverPort(MailSendObservationContext context) {
        if (context.getPort() <= 0) {
            return SERVER_PORT_UNKNOWN;
        }
        return KeyValue.of(LowCardinalityKeyNames.SERVER_PORT, String.valueOf(context.getPort()));
    }

    static KeyValue networkProtocolName(MailSendObservationContext context) {
        return KeyValue.of(LowCardinalityKeyNames.NETWORK_PROTOCOL_NAME, context.getProtocol());
    }

}
