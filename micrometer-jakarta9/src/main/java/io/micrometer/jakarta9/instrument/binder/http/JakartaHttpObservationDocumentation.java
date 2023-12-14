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
package io.micrometer.jakarta9.instrument.binder.http;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.binder.http.HttpObservationDocumentation.*;
import io.micrometer.jakarta9.instrument.binder.http.jaxrs.container.DefaultJaxRsContainerObservationConvention;
import io.micrometer.jakarta9.instrument.binder.http.jaxrs.client.DefaultJaxRsHttpClientObservationConvention;
import io.micrometer.jakarta9.instrument.binder.http.servlet.DefaultHttpServletObservationConvention;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documentation of HTTP based observations.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public enum JakartaHttpObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation created when a request is sent out via a JAX-RS client.
     */
    JAX_RS_CLIENT_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultJaxRsHttpClientObservationConvention.class;
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
     * Observation created when a request is received via JAX-RS API.
     */
    JAX_RS_CONTAINER_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultJaxRsContainerObservationConvention.class;
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
    SERVLET_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpServletObservationConvention.class;
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
