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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * An {@link ObservationDocumentation} for Jersey.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
@NonNullApi
public enum JerseyObservationDocumentation implements ObservationDocumentation {

    /**
     * Default observation for Jersey.
     */
    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultJerseyObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return JerseyLegacyLowCardinalityTags.values();
        }
    };

    @NonNullApi
    enum JerseyLegacyLowCardinalityTags implements KeyName {

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

        EXCEPTION {
            @Override
            public String asString() {
                return "exception";
            }
        },

        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        }

    }

}
