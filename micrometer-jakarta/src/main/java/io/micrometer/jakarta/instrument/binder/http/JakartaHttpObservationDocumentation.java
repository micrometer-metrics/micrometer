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
package io.micrometer.jakarta.instrument.binder.http;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.*;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documentation of HTTP based observations.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.12.0
 */
public enum JakartaHttpObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation created when a request is sent out via Jakarta API.
     */
    JAKARTA_CLIENT_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpJakartaClientRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(CommonLowCardinalityKeys.values(), ClientLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return KeyName.merge(CommonHighCardinalityKeys.values(), ClientHighCardinalityKeys.values());
        }
    },

    /**
     * Observation created when a request is received with Jakarta Http.
     */
    JAKARTA_SERVER_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpJakartaServerRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {

            return KeyName.merge(CommonLowCardinalityKeys.values(), ServerLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return CommonHighCardinalityKeys.values();
        }

    },

    /**
     * Observation created when a request is received with jakarta.servlet Http.
     */
    JAKARTA_SERVLET_SERVER_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpJakartaServerServletRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(CommonLowCardinalityKeys.values(), ServerLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return CommonHighCardinalityKeys.values();
        }

    };

}
