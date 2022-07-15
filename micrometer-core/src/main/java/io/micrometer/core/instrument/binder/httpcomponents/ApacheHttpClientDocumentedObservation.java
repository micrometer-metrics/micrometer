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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.docs.DocumentedObservation;

/**
 * {@link DocumentedObservation} for {@link MicrometerHttpRequestExecutor}.
 * @since 1.10.0
 */
public enum ApacheHttpClientDocumentedObservation implements DocumentedObservation {

    DEFAULT {
        @Override
        public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultApacheHttpClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return ApacheHttpClientTags.values();
        }
    };

    enum ApacheHttpClientTags implements KeyName {

        STATUS {
            @Override
            public String getKeyName() {
                return "status";
            }
        },
        METHOD {
            @Override
            public String getKeyName() {
                return "method";
            }
        },
        URI {
            @Override
            public String getKeyName() {
                return "uri";
            }
        },
        TARGET_SCHEME {
            @Override
            public String getKeyName() {
                return "target.scheme";
            }
        },
        TARGET_HOST {
            @Override
            public String getKeyName() {
                return "target.host";
            }
        },
        TARGET_PORT {
            @Override
            public String getKeyName() {
                return "target.port";
            }
        }

    }

}
