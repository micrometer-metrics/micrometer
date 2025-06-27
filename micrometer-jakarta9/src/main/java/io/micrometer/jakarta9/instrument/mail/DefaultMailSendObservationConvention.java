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

import io.micrometer.common.KeyValues;
import jakarta.mail.Message;

/**
 * Default implementation for {@link MailSendObservationConvention}.
 */
public class DefaultMailSendObservationConvention implements MailSendObservationConvention {

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
        return KeyValues.of(MailKeyValues.serverAddress(context), MailKeyValues.serverPort(context),
                MailKeyValues.networkProtocolName(context));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(MailSendObservationContext context) {
        Message message = context.getCarrier();
        return KeyValues.of(MailKeyValues.smtpMessageFrom(message), MailKeyValues.smtpMessageTo(message),
                MailKeyValues.smtpMessageSubject(message));
    }

}
