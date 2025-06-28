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
import io.micrometer.common.docs.KeyName;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.*;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.LowCardinalityKeyNames.*;

/**
 * Default implementation for {@link MailSendObservationConvention}.
 *
 * @since 1.16.0
 * @author famaridon
 */
public class DefaultMailSendObservationConvention implements MailSendObservationConvention {

    private static final String UNKNOWN = "unknown";

    @Override
    public String getName() {
        return "mail.send";
    }

    @Override
    public String getContextualName(MailSendObservationContext context) {
        return "mail send";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(MailSendObservationContext context) {
        return KeyValues.of(serverAddress(context), serverPort(context), networkProtocolName(context));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(MailSendObservationContext context) {
        Message message = context.getCarrier();
        List<KeyValue> values = new ArrayList<>();
        smtpMessageSubject(message).ifPresent(values::add);
        smtpMessageFrom(message).ifPresent(values::add);
        smtpMessageRecipients(message, RecipientType.TO).ifPresent(values::add);
        smtpMessageRecipients(message, RecipientType.CC).ifPresent(values::add);
        smtpMessageRecipients(message, RecipientType.BCC).ifPresent(values::add);

        return KeyValues.of(values);
    }

    private KeyValue serverAddress(MailSendObservationContext context) {
        String host = context.getHost();
        if (host == null || host.isEmpty()) {
            return SERVER_ADDRESS.withValue(UNKNOWN);
        }
        return SERVER_ADDRESS.withValue(host);
    }

    private KeyValue serverPort(MailSendObservationContext context) {
        int port = context.getPort();
        if (port <= 0) {
            return SERVER_PORT.withValue(UNKNOWN);
        }
        return SERVER_PORT.withValue(String.valueOf(port));
    }

    private KeyValue networkProtocolName(MailSendObservationContext context) {
        String protocol = context.getProtocol();
        if (protocol == null || protocol.isEmpty()) {
            return NETWORK_PROTOCOL_NAME.withValue(UNKNOWN);
        }
        return NETWORK_PROTOCOL_NAME.withValue(protocol);
    }

    private Optional<KeyValue> smtpMessageSubject(@Nullable Message message) {
        if (message == null) {
            return Optional.empty();
        }
        return safeExtractValue(SMTP_MESSAGE_SUBJECT, () -> Optional.ofNullable(message.getSubject()));
    }

    private Optional<KeyValue> smtpMessageFrom(@Nullable Message message) {
        if (message == null) {
            return Optional.empty();
        }
        return safeExtractValue(SMTP_MESSAGE_FROM, () -> addressesToValue(message.getFrom()));
    }

    private Optional<KeyValue> smtpMessageRecipients(@Nullable Message message, RecipientType recipientType) {
        if (message == null) {
            return Optional.empty();
        }
        MailObservationDocumentation.HighCardinalityKeyNames key = MailObservationDocumentation.HighCardinalityKeyNames
            .valueOf("SMTP_MESSAGE_" + recipientType.toString().toUpperCase(Locale.ROOT));
        return safeExtractValue(key, () -> addressesToValue(message.getRecipients(recipientType)));
    }

    Optional<KeyValue> smtpMessageId(@Nullable Message message) {
        if (message == null) {
            return Optional.empty();
        }
        return safeExtractValue(SMTP_MESSAGE_ID, () -> extractHeaderValue(message, "Message-ID"));
    }

    private Optional<String> extractHeaderValue(Message message, String headerName) throws MessagingException {
        String[] header = message.getHeader(headerName);
        if (header == null || header.length == 0) {
            return Optional.empty();
        }
        return Optional.of(String.join(", ", header));
    }

    private Optional<KeyValue> safeExtractValue(KeyName key, ValueExtractor extractor) {
        try {
            return extractor.extract().map(key::withValue);
        }
        catch (MessagingException ex) {
            return Optional.of(key.withValue(UNKNOWN));
        }
    }

    private Optional<String> addressesToValue(Address @Nullable [] addresses) {
        if (addresses == null || addresses.length == 0) {
            return Optional.empty();
        }
        String value = Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(", "));
        return Optional.of(value);
    }

    private interface ValueExtractor {

        Optional<String> extract() throws MessagingException;

    }

}
