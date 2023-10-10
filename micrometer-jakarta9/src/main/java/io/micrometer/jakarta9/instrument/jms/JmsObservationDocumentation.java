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

package io.micrometer.jakarta9.instrument.jms;

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.common.KeyValue KeyValues} for the observations on
 * {@link jakarta.jms.MessageProducer publication} and {@link jakarta.jms.MessageListener
 * processing} of {@link jakarta.jms.Message JMS messages}.
 * <p>
 * Use the {@link JmsInstrumentation} class to instrument an existing JMS session for
 * observability.
 *
 * @author Brian Clozel
 * @since 1.12.0
 * @see JmsInstrumentation
 */
public enum JmsObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation for JMS message publications. Measures the time spent sending a JMS
     * message to a destination by a {@link jakarta.jms.MessageProducer message producer}.
     */
    JMS_MESSAGE_PUBLISH {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultJmsPublishObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeyNames.values();
        }
    },

    /**
     * Observation for JMS message processing. Measures the time spent
     * {@link jakarta.jms.MessageListener processing a JMS message}.
     */
    JMS_MESSAGE_PROCESS {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultJmsProcessObservationConvention.class;
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
         * Name of the exception thrown during the operation, or
         * {@value KeyValue#NONE_VALUE} if no exception happened.
         */
        EXCEPTION {
            @Override
            public String asString() {
                return "exception";
            }
        },

        /**
         * Whether the destination (queue or topic) is temporary.
         */
        DESTINATION_TEMPORARY {
            @Override
            public String asString() {
                return "messaging.destination.temporary";
            }
        },

        /**
         * The name of the JMS operation being performed. Can be one of:
         * <ul>
         * <li>"publish" when {@link #JMS_MESSAGE_PUBLISH sending a JMS message to a
         * destination}
         * <li>"process" when a {@link #JMS_MESSAGE_PROCESS listener processes a received
         * message}
         * </ul>
         */
        OPERATION {
            @Override
            public String asString() {
                return "messaging.operation";
            }
        }

    }

    public enum HighCardinalityKeyNames implements KeyName {

        /**
         * The correlation ID of the JMS message.
         */
        CONVERSATION_ID {
            @Override
            public String asString() {
                return "messaging.message.conversation_id";
            }
        },

        /**
         * The name of destination the current message was sent to.
         */
        DESTINATION_NAME {
            @Override
            public String asString() {
                return "messaging.destination.name";
            }
        },

        /**
         * Value used by the messaging system as an identifier for the message.
         */
        MESSAGE_ID {
            @Override
            public String asString() {
                return "messaging.message.id";
            }
        }

    }

}
