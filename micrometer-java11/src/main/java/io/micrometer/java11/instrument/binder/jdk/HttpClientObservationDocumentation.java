/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.java11.instrument.binder.jdk;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum HttpClientObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation when an HTTP call is being made.
     */
    HTTP_CALL {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultHttpClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

    };

    enum LowCardinalityKeys implements KeyName {

        /**
         * HTTP Method.
         */
        METHOD {
            @Override
            public String asString() {
                return "method";
            }
        },

        /**
         * HTTP Status.
         */
        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        },

        /**
         * Key name for outcome.
         */
        OUTCOME {
            @Override
            public String asString() {
                return "outcome";
            }
        },

        /**
         * HTTP URI.
         */
        URI {
            @Override
            public String asString() {
                return "uri";
            }
        }

    }

}
