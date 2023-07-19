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
import io.micrometer.common.lang.Nullable;

/**
 * Contains conventions for naming and {@link KeyValues} providing.
 *
 * @param <T> type of context
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface ObservationConvention<T extends Observation.Context> extends KeyValuesConvention {

    /**
     * Empty instance of the convention.
     */
    ObservationConvention<Observation.Context> EMPTY = context -> false;

    /**
     * Low cardinality key values.
     * @return key values
     */
    default KeyValues getLowCardinalityKeyValues(T context) {
        return KeyValues.empty();
    }

    /**
     * High cardinality key values.
     * @return key values
     */
    default KeyValues getHighCardinalityKeyValues(T context) {
        return KeyValues.empty();
    }

    /**
     * Tells whether this observation convention should be applied for a given
     * {@link Observation.Context}.
     * @param context an {@link Observation.Context}
     * @return {@code true} when this observation convention should be used
     */
    boolean supportsContext(Observation.Context context);

    /**
     * Allows to override the name for an observation.
     * @return the new name for the observation
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * Allows to override the contextual name for an {@link Observation}. The
     * {@link Observation} will be renamed only when an explicit context was passed - if
     * an implicit context is used this method won't be called.
     * @param context context
     * @return the new, contextual name for the observation
     */
    @Nullable
    default String getContextualName(T context) {
        return null;
    }

}
