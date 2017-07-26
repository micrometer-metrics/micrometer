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

import com.google.common.cache.Cache;
import io.micrometer.core.instrument.binder.CacheMetrics;
import io.micrometer.core.instrument.binder.ExecutorServiceMetrics;
import io.micrometer.core.instrument.internal.TimedExecutorService;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Arrays.asList;

/**
 * @author Jon Schneider
 */
public class Meters {
    public static Builder build(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private Iterable<Tag> tags = Collections.emptyList();
        private Meter.Type type = Meter.Type.Other;

        Builder(String name) {
            this.name = name;
        }

        public Builder tags(Iterable<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public Builder tags(String... tags) {
            this.tags = Tags.zip(tags);
            return this;
        }

        public Builder type(Meter.Type type) {
            this.type = type;
            return this;
        }

        /**
         * @param measure A function of meter name to a set of measurements. The generated measurements
         *                will be enriched with the containing meter's tags automatically.
         * @return A custom meter
         */
        public Meter create(Function<String, List<Measurement>> measure) {
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
                public Type getType() {
                    return type;
                }

                @Override
                public List<Measurement> measure() {
                    List<Measurement> measurements = measure.apply(name);
                    measurements.forEach(m -> tags.forEach(t -> m.getTags().add(t)));
                    return measurements;
                }
            };
        }

        /**
         * @param obj     The monitored object. Access to this object's state from the meter must be thread safe. For
         *                example, if the monitored object is a collection type, ensure it is from the {@code java.util.concurrent} package.
         * @param measure A function of a meter name and a monitored object to a set of measurements. The generated measurements
         *                will be enriched with the containing meter's tags automatically. The monitored object is held with
         *                a weak reference, so as not to prevent garbage collection of the underlying object.
         * @return A custom meter. Once the underlying object has been garbage collected, this meter will emit an
         * empty set of measurements on sampling.
         */
        public <T> Meter create(T obj, BiFunction<String, T, List<Measurement>> measure) {
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
                public Type getType() {
                    return type;
                }

                @Override
                public List<Measurement> measure() {
                    if (ref.get() != null) {
                        List<Measurement> measurements = measure.apply(name, ref.get());
                        measurements.forEach(m -> tags.forEach(t -> m.getTags().add(t)));
                        return measurements;
                    }
                    return Collections.emptyList();
                }
            };
        }
    }

    /**
     * Record metrics on Guava caches.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Cache monitor(MeterRegistry registry, Cache cache, String name, Tag... tags) {
        new CacheMetrics(name, asList(tags), cache).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on Guava caches.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Cache monitor(MeterRegistry registry, Cache cache, String name, Iterable<Tag> tags) {
        new CacheMetrics(name, tags, cache).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String name, Iterable<Tag> tags) {
        final Timer commandTimer = registry.timer(name, tags);
        return commandTimer::record;
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String name, Tag... tags) {
        return monitor(registry, executor, name, asList(tags));
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String name, Iterable<Tag> tags) {
        new ExecutorServiceMetrics(executor, name, tags).bindTo(registry);
        return new TimedExecutorService(registry, executor, name, tags);
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String name, Tag... tags) {
        return monitor(registry, executor, name, asList(tags));
    }
}
