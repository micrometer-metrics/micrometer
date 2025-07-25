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
import org.junit.jupiter.api.Test;

import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.HighCardinalityKeyNames.*;
import static io.micrometer.jakarta9.instrument.mail.MailObservationDocumentation.LowCardinalityKeyNames.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MailObservationDocumentation}.
 *
 * @author famaridon
 */
class MailObservationDocumentationTest {

    @Test
    void testMailSendObservation() {
        MailObservationDocumentation mailSend = MailObservationDocumentation.MAIL_SEND;

        // Verify default convention
        assertThat(mailSend.getDefaultConvention()).isEqualTo(DefaultMailSendObservationConvention.class);

        // Verify low cardinality key names
        KeyName[] lowCardinalityKeyNames = mailSend.getLowCardinalityKeyNames();
        assertThat(lowCardinalityKeyNames).containsExactly(SERVER_ADDRESS, SERVER_PORT, NETWORK_PROTOCOL_NAME);

        // Verify high cardinality key names
        KeyName[] highCardinalityKeyNames = mailSend.getHighCardinalityKeyNames();
        assertThat(highCardinalityKeyNames).containsExactly(SMTP_MESSAGE_FROM, SMTP_MESSAGE_TO, SMTP_MESSAGE_CC,
                SMTP_MESSAGE_BCC, SMTP_MESSAGE_NEWSGROUPS, SMTP_MESSAGE_SUBJECT, SMTP_MESSAGE_ID);
    }

}
