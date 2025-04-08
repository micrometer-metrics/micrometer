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
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.common.KeyValue KeyValues} for the observations on
 * {@link jakarta.mail.Transport send} of mail messages.
 *
 * @since 1.15.0
 */
public enum MailObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation for mail send operations. Measures the time spent sending a mail
     * message.
     */
    MAIL_SEND {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultMailSendObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    };

    public enum LowCardinalityKeyNames implements KeyName {

        /**
         * (SMTP) Server address used for sending mails.
         */
        SERVER_ADDRESS {
            @Override
            public String asString() {
                return "server.address";
            }
        },
        /**
         * (SMTP) Server port used for sending mails.
         */
        SERVER_PORT {
            @Override
            public String asString() {
                return "server.port";
            }
        },
        /**
         * Network protocol used for sending mails.
         */
        NETWORK_PROTOCOL_NAME {
            @Override
            public String asString() {
                return "network.protocol.name";
            }
        }

    }

    public enum HighCardinalityKeyNames implements KeyName {

        /**
         * Sender of the mail.
         */
        SMTP_MESSAGE_FROM {
            @Override
            public String asString() {
                return "smtp.message.from";
            }
        },
        /**
         * Primary (TO) recipient(s) of the mail.
         */
        SMTP_MESSAGE_TO {
            @Override
            public String asString() {
                return "smtp.message.to";
            }
        },
        /**
         * Carbon copy (CC) recipient(s) of the mail.
         */
        SMTP_MESSAGE_CC {
            @Override
            public String asString() {
                return "smtp.message.cc";
            }
        },
        /**
         * Blind carbon copy (BCC) recipient(s) of the mail.
         */
        SMTP_MESSAGE_BCC {
            @Override
            public String asString() {
                return "smtp.message.bcc";
            }
        },
        /**
         * Subject line of the mail.
         */
        SMTP_MESSAGE_SUBJECT {
            @Override
            public String asString() {
                return "smtp.message.subject";
            }
        },
        /**
         * Message ID received from the SMTP server.
         */
        SMTP_MESSAGE_ID {
            @Override
            public String asString() {
                return "smtp.message.id";
            }
        }

    }

}
