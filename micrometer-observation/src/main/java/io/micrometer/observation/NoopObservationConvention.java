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
package io.micrometer.observation;

import io.micrometer.common.KeyValues;

/**
 * No-op implementation of {@link ObservationConvention} so that we can disable the
 * instrumentation logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
final class NoopObservationConvention implements ObservationConvention<Observation.Context> {

    /**
     * Instance of {@link NoopObservationConvention}.
     */
    static final NoopObservationConvention INSTANCE = new NoopObservationConvention();

    private NoopObservationConvention() {
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getContextualName(Observation.Context context) {
        return "";
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return ObservationConvention.EMPTY.supportsContext(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
        return ObservationConvention.EMPTY.getLowCardinalityKeyValues(context);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
        return ObservationConvention.EMPTY.getHighCardinalityKeyValues(context);
    }

}
