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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import org.junit.jupiter.api.Test;

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
        Class<? extends ObservationConvention<? extends Observation.Context>> defaultConvention = mailSend
            .getDefaultConvention();
        assertThat(defaultConvention).isEqualTo(DefaultMailSendObservationConvention.class);

        // Verify low cardinality key names
        KeyName[] lowCardinalityKeyNames = mailSend.getLowCardinalityKeyNames();
        assertThat(lowCardinalityKeyNames).containsExactly(
                MailObservationDocumentation.LowCardinalityKeyNames.SERVER_ADDRESS,
                MailObservationDocumentation.LowCardinalityKeyNames.SERVER_PORT,
                MailObservationDocumentation.LowCardinalityKeyNames.NETWORK_PROTOCOL_NAME);

        // Verify high cardinality key names
        KeyName[] highCardinalityKeyNames = mailSend.getHighCardinalityKeyNames();
        assertThat(highCardinalityKeyNames).containsExactly(
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_FROM,
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_TO,
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_CC,
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_BCC,
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_SUBJECT,
                MailObservationDocumentation.HighCardinalityKeyNames.SMTP_MESSAGE_ID);
    }

}
