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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for the Jetty HTTP client.
 *
 * @since 1.11.0
 * @see JettyClientMetrics
 * @deprecated since 1.13.0 in favor of the micrometer-jetty12 module
 */
@Deprecated
public enum JettyClientObservationDocumentation implements ObservationDocumentation {

    /**
     * Default instrumentation from {@link JettyClientMetrics}.
     */
    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return JettyClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return JettyClientLowCardinalityTags.values();
        }
    };

    enum JettyClientLowCardinalityTags implements KeyName {

        /**
         * URI of the request. Ideally it should be the templated URI pattern to maintain
         * low cardinality and support useful aggregation.
         */
        URI {
            @Override
            public String asString() {
                return "uri";
            }
        },
        /**
         * Exception thrown, if any.
         */
        EXCEPTION {
            @Override
            public String asString() {
                return "exception";
            }
        },
        /**
         * HTTP method of the request, if available.
         */
        METHOD {
            @Override
            public String asString() {
                return "method";
            }
        },
        /**
         * Description of the outcome of an HTTP request based on the HTTP status code
         * category, if known.
         */
        OUTCOME {
            @Override
            public String asString() {
                return "outcome";
            }
        },
        /**
         * HTTP status of the response, if available.
         */
        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        },
        /**
         * Host used in the request.
         */
        HOST {
            @Override
            public String asString() {
                return "host";
            }
        }

    }

}
