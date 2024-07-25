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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Provides names and {@link io.micrometer.common.KeyValues} for Jersey request
 * observations.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public interface JerseyObservationConvention extends ObservationConvention<JerseyContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof JerseyContext;
    }

}
