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

    private static final KeyValue NETWORK_PROTOCOL_NAME_UNKNOWN = NETWORK_PROTOCOL_NAME.withValue(UNKNOWN);

    private static final KeyValue SERVER_PORT_UNKNOWN = SERVER_PORT.withValue(UNKNOWN);

    private static final KeyValue SERVER_ADDRESS_UNKNOWN = SERVER_ADDRESS.withValue(UNKNOWN);

    private static final KeyValue SMTP_MESSAGE_SUBJECT_UNKNOWN = SMTP_MESSAGE_SUBJECT.withValue(UNKNOWN);

    private static final KeyValue SMTP_MESSAGE_FROM_UNKNOWN = SMTP_MESSAGE_FROM.withValue(UNKNOWN);

    private static final KeyValue SMTP_MESSAGE_ID_UNKNOWN = SMTP_MESSAGE_ID.withValue(UNKNOWN);

    private static final Map<RecipientType, KeyName> RECIPIENT_TYPE_KEY_NAME_MAP;
    static {
        Map<RecipientType, KeyName> map = new IdentityHashMap<>();
        map.put(RecipientType.TO, SMTP_MESSAGE_TO);
        map.put(RecipientType.CC, SMTP_MESSAGE_CC);
        map.put(RecipientType.BCC, SMTP_MESSAGE_BCC);
        RECIPIENT_TYPE_KEY_NAME_MAP = map;
    }

    private static final Map<RecipientType, KeyValue> RECIPIENT_TYPE_UNKNOWN_MAP;
    static {
        Map<RecipientType, KeyValue> map = new IdentityHashMap<>();
        map.put(RecipientType.TO, SMTP_MESSAGE_TO.withValue(UNKNOWN));
        map.put(RecipientType.CC, SMTP_MESSAGE_CC.withValue(UNKNOWN));
        map.put(RecipientType.BCC, SMTP_MESSAGE_BCC.withValue(UNKNOWN));
        RECIPIENT_TYPE_UNKNOWN_MAP = map;
    }

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
        if (message == null) {
            return KeyValues.empty();
        }

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
            return SERVER_ADDRESS_UNKNOWN;
        }
        return SERVER_ADDRESS.withValue(host);
    }

    private KeyValue serverPort(MailSendObservationContext context) {
        int port = context.getPort();
        if (port <= 0) {
            return SERVER_PORT_UNKNOWN;
        }
        return SERVER_PORT.withValue(String.valueOf(port));
    }

    private KeyValue networkProtocolName(MailSendObservationContext context) {
        String protocol = context.getProtocol();
        if (protocol == null || protocol.isEmpty()) {
            return NETWORK_PROTOCOL_NAME_UNKNOWN;
        }
        return NETWORK_PROTOCOL_NAME.withValue(protocol);
    }

    private Optional<KeyValue> smtpMessageSubject(Message message) {
        return safeExtractValue(SMTP_MESSAGE_SUBJECT, () -> Optional.ofNullable(message.getSubject()),
                SMTP_MESSAGE_SUBJECT_UNKNOWN);
    }

    private Optional<KeyValue> smtpMessageFrom(Message message) {
        return safeExtractValue(SMTP_MESSAGE_FROM, () -> addressesToValue(message.getFrom()),
                SMTP_MESSAGE_FROM_UNKNOWN);
    }

    private Optional<KeyValue> smtpMessageRecipients(Message message, RecipientType recipientType) {
        KeyName keyName = Objects.requireNonNull(RECIPIENT_TYPE_KEY_NAME_MAP.get(recipientType));
        KeyValue unknownValue = Objects.requireNonNull(RECIPIENT_TYPE_UNKNOWN_MAP.get(recipientType));
        return safeExtractValue(keyName, () -> addressesToValue(message.getRecipients(recipientType)), unknownValue);
    }

    Optional<KeyValue> smtpMessageId(Message message) {
        return safeExtractValue(SMTP_MESSAGE_ID, () -> extractHeaderValue(message, "Message-ID"),
                SMTP_MESSAGE_ID_UNKNOWN);
    }

    private Optional<String> extractHeaderValue(Message message, String headerName) throws MessagingException {
        String[] header = message.getHeader(headerName);
        if (header == null || header.length == 0) {
            return Optional.empty();
        }
        return Optional.of(String.join(", ", header));
    }

    private Optional<KeyValue> safeExtractValue(KeyName key, ValueExtractor extractor, KeyValue unknownValue) {
        try {
            return extractor.extract().map(key::withValue);
        }
        catch (MessagingException ex) {
            return Optional.of(unknownValue);
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
