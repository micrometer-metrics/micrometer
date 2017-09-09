/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

public interface Gauge extends Meter {
    /**
     * Returns the current value. The act of observing the value by calling this method triggers sampling
     * of the underlying number or user-defined function that defines the value for the gauge.
     */
    double value();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::value, Statistic.Value));
    }

    @Override
    default Type getType() {
        return Type.Gauge;
    }

    static <T> Builder builder(String name, T obj, ToDoubleFunction<T> f) {
        return new Builder<>(name, obj, f);
    }

    class Builder<T> {
        private final String name;
        private final T obj;
        private final ToDoubleFunction<T> f;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private Builder(String name, T obj, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.f = f;
        }

        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }
        
        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        public Gauge register(MeterRegistry registry) {
            return registry.gauge(registry.createId(name, tags, description, baseUnit), obj, f);
        }
    }
}
