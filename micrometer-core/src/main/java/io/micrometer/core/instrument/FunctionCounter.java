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

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * A counter that tracks a monotonically increasing function.
 *
 * @author Jon Schneider
 */
public interface FunctionCounter extends Meter {

    /**
     * Build a function-tracking counter that produces a monotonically increasing count
     * from the state object.
     * @param name The counter's name.
     * @param obj State object used to compute a count.
     * @param f Function that produces a monotonically increasing count from the state
     * object.
     * @param <T> The type of the state object.
     * @return A new function counter builder.
     */
    static <T> Builder<T> builder(String name, T obj, ToDoubleFunction<T> f) {
        return new Builder<>(name, obj, f);
    }

    /**
     * Build a function-tracking counter for a function that tracks monotonically
     * increasing time. The time value will automatically scale to the base time unit
     * expected by each registry implementation.
     * @param name The counter's name.
     * @param obj State object used to compute a value.
     * @param objToCountFunction Function that produces a monotonically increasing time
     * count from the state object.
     * @param sourceUnit Time unit of the value returned by the count function.
     * @param <T> The type of the state object.
     * @return A new time-based function counter builder.
     * @since 1.18.0
     */
    static <T> TimeBasedBuilder<T> builder(String name, T obj, ToDoubleFunction<T> objToCountFunction,
            TimeUnit sourceUnit) {
        return new TimeBasedBuilder<>(name, obj, objToCountFunction, sourceUnit);
    }

    /**
     * @return The cumulative count since this counter was created.
     */
    double count();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::count, Statistic.COUNT));
    }

    /**
     * Fluent builder for function counters.
     *
     * @param <T> The type of the state object from which the counter value is extracted.
     */
    class Builder<T> {

        private final String name;

        private final ToDoubleFunction<T> f;

        private Tags tags = Tags.empty();

        private final T obj;

        private @Nullable String description;

        private @Nullable String baseUnit;

        private Builder(String name, T obj, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.f = f;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
         * @return The function counter builder with added tags.
         */
        public Builder<T> tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual function counter.
         * @return The function counter builder with added tags.
         */
        public Builder<T> tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key The tag key.
         * @param value The tag value.
         * @return The function counter builder with a single added tag.
         */
        public Builder<T> tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual function counter.
         * @return The function counter builder with added description.
         */
        public Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Use this if the unit is not time. If the unit is time, use a time-based builder
         * instead:
         * {@link FunctionCounter#builder(String, Object, ToDoubleFunction, TimeUnit)}.
         * @param unit Base unit of the eventual counter.
         * @return The counter builder with added base unit.
         */
        public Builder<T> baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        /**
         * Register the function counter with the registry. If a meter with the same name
         * and tags is already registered, the existing function counter is returned and
         * this registration call has no effect.
         * @param registry Registry to register the function counter with.
         * @return The registered function counter.
         */
        public FunctionCounter register(MeterRegistry registry) {
            return registry.more().counter(new Meter.Id(name, tags, baseUnit, description, Type.COUNTER), obj, f);
        }

    }

    /**
     * Fluent builder for function counters that count monotonically increasing time.
     *
     * @param <T> The type of the state object from which the counter value is extracted.
     * @since 1.18.0
     */
    class TimeBasedBuilder<T> {

        private final String name;

        private final ToDoubleFunction<T> objToCountFunction;

        private Tags tags = Tags.empty();

        private final T obj;

        private @Nullable String description;

        private final TimeUnit sourceUnit;

        private TimeBasedBuilder(String name, T obj, ToDoubleFunction<T> objToCountFunction, TimeUnit sourceUnit) {
            this.name = name;
            this.obj = obj;
            this.objToCountFunction = objToCountFunction;
            this.sourceUnit = sourceUnit;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
         * @return The function counter builder with added tags.
         */
        public TimeBasedBuilder<T> tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual function counter.
         * @return The function counter builder with added tags.
         */
        public TimeBasedBuilder<T> tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key The tag key.
         * @param value The tag value.
         * @return The function counter builder with a single added tag.
         */
        public TimeBasedBuilder<T> tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual function counter.
         * @return The function counter builder with added description.
         */
        public TimeBasedBuilder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Register the function counter with the registry. If a meter with the same name
         * and tags is already registered, the existing function counter is returned and
         * this registration call has no effect.
         * @param registry Registry to register the function counter with.
         * @return The registered function counter.
         */
        public FunctionCounter register(MeterRegistry registry) {
            return registry.more()
                .functionTimeCounter(new Meter.Id(name, tags, null, description, Type.COUNTER), obj, objToCountFunction,
                        sourceUnit);
        }

    }

}
