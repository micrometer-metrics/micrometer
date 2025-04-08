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

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.LowCardinalityKeyNames;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import io.micrometer.common.KeyValue;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Message.RecipientType;

class MailKeyValues {

    /**
     * The value is when value can't be determined.
     */
    public static final String UNKNOWN = "unknown";

    /**
     * The value is when the value is not set or empty.
     */
    public static final String EMPTY = "";

    private MailKeyValues() {
    }

    static KeyValue smtpMessageFrom(Message message) {
        return safeExtractValue(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_FROM,
                () -> addressesToValue(message.getFrom()));
    }

    static KeyValue smtpMessageRecipients(Message message, RecipientType recipientType) {
        MailObservationDocumentation.HighCardinalityKeyNames key = MailObservationDocumentation.HighCardinalityKeyNames
            .valueOf("SMTP_MESSAGE_" + recipientType.toString().toUpperCase(Locale.ROOT));
        return safeExtractValue(key, () -> addressesToValue(message.getRecipients(recipientType)));
    }

    static KeyValue smtpMessageSubject(Message message) {

        return safeExtractValue(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_SUBJECT,
                message::getSubject);
    }

    static KeyValue smtpMessageId(Message message) {
        return safeExtractValue(MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_ID, () -> {
            String[] header = message.getHeader("Message-ID");
            if (header == null || header.length == 0) {
                return EMPTY;
            }
            return String.join(", ", header);
        });
    }

    static KeyValue serverAddress(MailSendObservationContext context) {
        String host = context.getHost();
        if (host == null || host.isEmpty()) {
            host = UNKNOWN;
        }
        return LowCardinalityKeyNames.SERVER_ADDRESS.withValue(host);
    }

    static KeyValue serverPort(MailSendObservationContext context) {
        String port = UNKNOWN;
        if (context.getPort() > 0) {
            port = String.valueOf(context.getPort());
        }
        return LowCardinalityKeyNames.SERVER_PORT.withValue(port);
    }

    static KeyValue networkProtocolName(MailSendObservationContext context) {
        String protocol = context.getProtocol();
        if (protocol == null || protocol.isEmpty()) {
            protocol = UNKNOWN;
        }
        return KeyValue.of(LowCardinalityKeyNames.NETWORK_PROTOCOL_NAME, protocol);
    }

    private static KeyValue safeExtractValue(KeyName key, ValueExtractor extractor) {
        String value;
        try {
            value = extractor.extract();
            if (value == null || value.isEmpty()) {
                value = EMPTY;
            }
        }
        catch (MessagingException exc) {
            value = UNKNOWN;
        }
        return key.withValue(value);
    }

    private static String addressesToValue(@Nullable Address[] addresses) {
        String value;
        if (addresses == null || addresses.length == 0) {
            value = EMPTY;
        }
        else {
            value = Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(", "));
        }
        return value;
    }

    private interface ValueExtractor {

        @Nullable
        String extract() throws MessagingException;

    }

}
