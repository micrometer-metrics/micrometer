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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;

import java.util.Collections;

/**
 * Counters monitor monotonically increasing values. Counters may never be reset to a
 * lesser value. If you need to track a value that goes up and down, use a {@link Gauge}.
 *
 * @author Jon Schneider
 * @author Jonatan Ivanov
 */
public interface Counter extends Meter {

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Update the counter by one.
     */
    default void increment() {
        increment(1.0);
    }

    /**
     * Update the counter by {@code amount}.
     * @param amount Amount to add to the counter.
     */
    void increment(double amount);

    /**
     * @return The cumulative count since this counter was created.
     */
    double count();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::count, Statistic.COUNT));
    }

    /**
     * Fluent builder for counters.
     */
    class Builder {

        private final String name;

        private Tags tags = Tags.empty();

        @Nullable
        private String description;

        @Nullable
        private String baseUnit;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
         * @return The counter builder with added tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual counter.
         * @return The counter builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key The tag key.
         * @param value The tag value.
         * @return The counter builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual counter.
         * @return The counter builder with added description.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * @param unit Base unit of the eventual counter.
         * @return The counter builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        /**
         * Convenience method to create meters from the builder that only differ in tags.
         * This method can be used for dynamic tagging by creating the builder once and
         * applying the dynamically changing tags using the returned
         * {@link MeterProvider}.
         * @param registry A registry to add the meter to, if it doesn't already exist.
         * @return A {@link MeterProvider} that returns a meter based on the provided
         * tags.
         * @since 1.12.0
         */
        public MeterProvider<Counter> withRegistry(MeterRegistry registry) {
            return extraTags -> register(registry, tags.and(extraTags));
        }

        /**
         * Add the counter to a single registry, or return an existing counter in that
         * registry. The returned counter will be unique for each registry, but each
         * registry is guaranteed to only create one counter for the same combination of
         * name and tags.
         * @param registry A registry to add the counter to, if it doesn't already exist.
         * @return A new or existing counter.
         */
        public Counter register(MeterRegistry registry) {
            return register(registry, tags);
        }

        private Counter register(MeterRegistry registry, Tags tags) {
            return registry.counter(new Meter.Id(name, tags, baseUnit, description, Type.COUNTER));
        }

    }

}
