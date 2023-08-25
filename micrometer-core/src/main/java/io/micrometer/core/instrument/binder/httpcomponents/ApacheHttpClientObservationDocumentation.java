/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for Apache HTTP client instrumentation.
 * <p>
 * See
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationDocumentation}
 * for Apache HTTP client 5 support.
 *
 * @since 1.10.0
 * @see MicrometerHttpRequestExecutor
 * @deprecated as of 1.12.0 in favor of HttpComponents 5.x and
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationDocumentation}.
 */
@Deprecated
public enum ApacheHttpClientObservationDocumentation implements ObservationDocumentation {

    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultApacheHttpClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return ApacheHttpClientKeyNames.values();
        }
    };

    enum ApacheHttpClientKeyNames implements KeyName {

        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        },

        /**
         * Key name for outcome.
         * @since 1.11.0
         */
        OUTCOME {
            @Override
            public String asString() {
                return "outcome";
            }
        },
        METHOD {
            @Override
            public String asString() {
                return "method";
            }
        },
        URI {
            @Override
            public String asString() {
                return "uri";
            }
        },
        TARGET_SCHEME {
            @Override
            public String asString() {
                return "target.scheme";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        },
        TARGET_HOST {
            @Override
            public String asString() {
                return "target.host";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        },
        TARGET_PORT {
            @Override
            public String asString() {
                return "target.port";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        }

    }

}
