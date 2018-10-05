/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.wavefront;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.step.StepCounter;
import io.micrometer.core.lang.Nullable;

import static io.micrometer.wavefront.WavefrontConstants.WAVEFRONT_METRIC_TYPE_TAG_KEY;
import static io.micrometer.wavefront.WavefrontConstants.isWavefrontMetricType;

/**
 * Wavefront delta counter which has the ability to report delta values aggregated on Wavefront
 * server side. Extends StepCounter so that the value is reset every time it is reported.
 *
 * @author Han Zhang
 */
public class DeltaCounter extends StepCounter {
    /**
     * The tag value that is used to identify DeltaCounters.
     */
    private static final String WAVEFRONT_METRIC_TYPE_TAG_VALUE = "deltaCounter";

    static Builder builder(String name) { return new Builder(name); }

    /**
     * @param id    The identifier for a metric.
     * @return {@code true} if the id identifies a DeltaCounter, {@code false} otherwise.
     */
    static boolean isDeltaCounter(Id id) {
        return isWavefrontMetricType(id, WAVEFRONT_METRIC_TYPE_TAG_VALUE);
    }

    DeltaCounter(Id id, Clock clock, long stepMillis) {
        super(id, clock, stepMillis);
    }

    /**
     * Fluent builder for delta counters.
     */
    static class Builder {
        private final Counter.Builder builder;

        private Builder(String name) {
            builder = Counter
                .builder(name)
                .tag(WAVEFRONT_METRIC_TYPE_TAG_KEY, WAVEFRONT_METRIC_TYPE_TAG_VALUE);
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The delta counter builder with added tags.
         */
        public Builder tags(String... tags) {
            builder.tags(tags);
            return this;
        }

        /**
         * @param tags Tags to add to the eventual counter.
         * @return The delta counter builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            builder.tags(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The delta counter builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            builder.tag(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual counter.
         * @return The delta counter builder with added description.
         */
        public Builder description(@Nullable String description) {
            builder.description(description);
            return this;
        }

        /**
         * @param unit Base unit of the eventual counter.
         * @return The delta counter builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            builder.baseUnit(unit);
            return this;
        }

        /**
         * Add the delta counter to a single registry, or return an existing delta counter
         * in that registry. The returned counter will be unique for each registry, but each
         * registry is guaranteed to only create one counter for the same combination of
         * name and tags. If an existing counter is found but it is not a delta counter,
         * an IllegalStateException is thrown.
         *
         * @param registry A registry to add the delta counter to, if it doesn't already exist.
         * @return A new or existing delta counter.
         */
        public DeltaCounter register(MeterRegistry registry) {
            Counter counter = builder.register(registry);
            if (counter instanceof DeltaCounter) {
                return (DeltaCounter) counter;
            } else {
                throw new IllegalStateException("Found existing non-DeltaCounter: " + counter);
            }
        }
    }
}
