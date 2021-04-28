/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * A specialized gauge that tracks a time value, to be scaled to the base unit of time expected by each registry implementation.
 *
 * @author Jon Schneider
 */
public interface TimeGauge extends Gauge {
    static <T> Builder<T> builder(String name, @Nullable T obj, TimeUnit fUnits, ToDoubleFunction<T> f) {
        return new Builder<>(name, obj, fUnits, f);
    }

    /**
     * @return The base time unit of the timer to which all published metrics will be scaled
     */
    TimeUnit baseTimeUnit();

    /**
     * The act of observing the value by calling this method triggers sampling
     * of the underlying number or user-defined function that defines the value for the gauge.
     *
     * @param unit The base unit of time to scale the value to.
     * @return The current value, scaled to the appropriate base unit.
     */
    default double value(TimeUnit unit) {
        return TimeUtils.convert(value(), baseTimeUnit(), unit);
    }

    /**
     * Fluent builder for time gauges.
     */
    class Builder<T> {
        private final String name;
        private final TimeUnit fUnits;
        private final ToDoubleFunction<T> f;
        private Tags tags = Tags.empty();

        @Nullable
        private final T obj;

        @Nullable
        private String description;

        private Builder(String name, @Nullable T obj, TimeUnit fUnits, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.fUnits = fUnits;
            this.f = f;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return This time gauge builder.
         */
        public Builder<T> tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual time gauge.
         * @return The time gauge builder with added tags.
         */
        public Builder<T> tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The time gauge builder with a single added tag.
         */
        public Builder<T> tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual time gauge.
         * @return The time gauge builder with added description.
         */
        public Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Add the time gauge to a single registry, or return an existing time gauge in that registry. The returned
         * time gauge will be unique for each registry, but each registry is guaranteed to only create one time gauge
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the time gauge to, if it doesn't already exist.
         * @return A new or existing time gauge.
         */
        public TimeGauge register(MeterRegistry registry) {
            return registry.more().timeGauge(new Meter.Id(name, tags, null, description, Type.GAUGE),
                    obj, fUnits, f);
        }
    }
}
