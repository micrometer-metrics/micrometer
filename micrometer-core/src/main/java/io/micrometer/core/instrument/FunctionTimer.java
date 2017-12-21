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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.util.Assert;
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public interface FunctionTimer extends Meter {
    /**
     * The total number of occurrences of the timed event.
     */
    double count();

    /**
     * The total time of all occurrences of the timed event.
     */
    double totalTime(TimeUnit unit);

    default double mean(TimeUnit unit) {
        Assert.notNull(unit, "timeUnit");
        return count() == 0 ? 0 : totalTime(unit) / count();
    }

    TimeUnit baseTimeUnit();

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(this::count, Statistic.Count),
            new Measurement(() -> totalTime(baseTimeUnit()), Statistic.TotalTime)
        );
    }

    static <T> Builder<T> builder(String name, T obj, ToLongFunction<T> countFunction,
                                  ToDoubleFunction<T> totalTimeFunction,
                                  TimeUnit totalTimeFunctionUnits) {
        return new Builder<>(name, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
    }

    class Builder<T> {
        private final String name;
        private final T obj;
        private final ToLongFunction<T> countFunction;
        private final ToDoubleFunction<T> totalTimeFunction;
        private final TimeUnit totalTimeFunctionUnits;
        private final List<Tag> tags = new ArrayList<>();
        private @Nullable String description;
        private @Nullable String baseUnit;

        private Builder(String name, T obj,
                        ToLongFunction<T> countFunction,
                        ToDoubleFunction<T> totalTimeFunction,
                        TimeUnit totalTimeFunctionUnits) {
            Assert.notNull(name, "name");
            Assert.notNull(obj, "obj");
            Assert.notNull(countFunction, "countFunction");
            Assert.notNull(totalTimeFunction, "totalTimeFunction");
            Assert.notNull(totalTimeFunctionUnits, "totalTimeFunctionUnits");
            this.name = name;
            this.obj = obj;
            this.countFunction = countFunction;
            this.totalTimeFunction = totalTimeFunction;
            this.totalTimeFunctionUnits = totalTimeFunctionUnits;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder<T> tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder<T> tags(Iterable<Tag> tags) {
            Assert.notNull(tags,"tags");
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder<T> tag(String key, String value) {
            tags.add(Tag.of(key, value));
            return this;
        }

        public Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder<T> baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        public FunctionTimer register(MeterRegistry registry) {
            Assert.notNull(registry,"registry");
            return registry.more().timer(new Meter.Id(name, tags, baseUnit, description, Type.Timer), obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnits);
        }
    }
}
