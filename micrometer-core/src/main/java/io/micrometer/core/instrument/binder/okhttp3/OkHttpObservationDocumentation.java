/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * An {@link ObservationDocumentation} for OkHttp3 metrics.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
@NonNullApi
public enum OkHttpObservationDocumentation implements ObservationDocumentation {

    /**
     * Default observation for OK HTTP.
     */
    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultOkHttpObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return OkHttpLegacyLowCardinalityTags.values();
        }
    };

    @NonNullApi
    enum OkHttpLegacyLowCardinalityTags implements KeyName {

        TARGET_SCHEME {
            @Override
            public String asString() {
                return "target.scheme";
            }
        },

        TARGET_HOST {
            @Override
            public String asString() {
                return "target.host";
            }
        },

        TARGET_PORT {
            @Override
            public String asString() {
                return "target.port";
            }
        },

        HOST {
            @Override
            public String asString() {
                return "host";
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
        }

    }

}
