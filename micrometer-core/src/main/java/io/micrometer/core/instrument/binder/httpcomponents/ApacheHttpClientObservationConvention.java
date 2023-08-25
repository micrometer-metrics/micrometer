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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} for Apache HTTP client instrumentation.
 * <p>
 * See
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationConvention}
 * for Apache HTTP client 5 support.
 *
 * @since 1.10.0
 * @see DefaultApacheHttpClientObservationConvention
 * @deprecated as of 1.12.0 in favor of HttpComponents 5.x and
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationConvention}.
 */
@Deprecated
public interface ApacheHttpClientObservationConvention extends ObservationConvention<ApacheHttpClientContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof ApacheHttpClientContext;
    }

}
