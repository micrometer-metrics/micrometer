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
package org.springframework.metrics.instrument;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Jon Schneider
 */
public class Meters {
    public static Builder build(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private Iterable<Tag> tags;

        Builder(String name) {
            this.name = name;
        }

        Builder tags(Iterable<Tag> tags) {
            this.tags = tags;
            return this;
        }

        Builder tags(Tag... tags) {
            this.tags = Arrays.asList(tags);
            return this;
        }

        Builder tags(String... tags) {
            this.tags = Tag.tags(tags);
            return this;
        }

        /**
         * @param measure A function of meter name to a set of measurements. The generated measurements
         *                will be enriched with the containing meter's tags automatically.
         * @return A custom meter
         */
        Meter create(Function<String, Iterable<Measurement>> measure) {
            return new Meter() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Iterable<Tag> getTags() {
                    return tags;
                }

                @Override
                public Iterable<Measurement> measure() {
                    Iterable<Measurement> measurements = measure.apply(name);
                    measurements.forEach(m -> tags.forEach(t -> m.getTags().add(t)));
                    return measurements;
                }
            };
        }

        /**
         * @param measure A function of a meter name and a monitored object to a set of measurements. The generated measurements
         *                will be enriched with the containing meter's tags automatically. The monitored object is held with
         *                a weak reference, so as not to prevent garbage collection of the underlying object.
         * @return A custom meter. Once the underlying object has been garbage collected, this meter will emit an
         * empty set of measurements on sampling.
         */
        <T> Meter create(T obj, BiFunction<String, T, Iterable<Measurement>> measure) {
            return new Meter() {
                private WeakReference<T> ref = new WeakReference<>(obj);

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public Iterable<Tag> getTags() {
                    return tags;
                }

                @Override
                public Iterable<Measurement> measure() {
                    if (ref.get() != null) {
                        Iterable<Measurement> measurements = measure.apply(name, ref.get());
                        measurements.forEach(m -> tags.forEach(t -> m.getTags().add(t)));
                        return measurements;
                    }
                    return Collections.emptyList();
                }
            };
        }
    }
}
