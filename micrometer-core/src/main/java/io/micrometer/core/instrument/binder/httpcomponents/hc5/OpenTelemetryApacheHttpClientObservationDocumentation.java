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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for Apache HTTP Client 5 instrumentation based on
 * OpenTelemetry Semantic Conventions v1.36.0 for HTTP.
 *
 * @since 1.16.0
 * @see ObservationExecChainHandler
 */
public enum OpenTelemetryApacheHttpClientObservationDocumentation implements ObservationDocumentation {

    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return OpenTelemetryApacheHttpClientObservationConvention.class;
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

    enum LowCardinalityKeyNames implements KeyName {

        METHOD {
            @Override
            public String asString() {
                return "http.request.method";
            }
        },
        SERVER_ADDRESS {
            @Override
            public String asString() {
                return "server.address";
            }
        },
        SERVER_PORT {
            @Override
            public String asString() {
                return "server.port";
            }
        },
        ERROR_TYPE {
            @Override
            public String asString() {
                return "error.type";
            }
        },
        STATUS {
            @Override
            public String asString() {
                return "http.response.status_code";
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

    }

    enum HighCardinalityKeyNames implements KeyName {

        URL {
            @Override
            public String asString() {
                return "url.full";
            }
        },
        /**
         * Original HTTP method sent by the client in the request line. This may differ
         * from the {@code {http.request.method}} if an unknown method is used.
         */
        METHOD_ORIGINAL {
            @Override
            public String asString() {
                return "http.request.method_original";
            }
        }

    }

}
