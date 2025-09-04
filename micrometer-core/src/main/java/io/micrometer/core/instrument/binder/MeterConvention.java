/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.binder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * Convention describing a {@link Meter} used for a specific instrumentation. Different
 * implementations of this convention may change the convention used by the same
 * instrumentation logic. If the instrumentation is timing an operation, consider using
 * the {@link Observation} API and {@link ObservationConvention} instead.
 * {@link MeterConvention} is for instrumentation that is instrumented directly with the
 * metrics API.
 *
 * @param <C> context type used to derive tags
 * @since 1.16.0
 */
// Work around NullAway. C might be Void, and thus we pass null to getTags.
@NullUnmarked
public interface MeterConvention<C extends @Nullable Object> {

    /**
     * Canonical name of the meter.
     * @return meter name
     */
    @NonNull String getName();

    /**
     * Tags specific to this meter convention. Generally they should be combined with any
     * common tags if this convention is associated with other conventions that share
     * tags.
     * @param context optional context which may be used for deriving tags
     * @return tags instance to use with the meter described by this convention
     */
    default @NonNull Tags getTags(C context) {
        return Tags.empty();
    }

}
