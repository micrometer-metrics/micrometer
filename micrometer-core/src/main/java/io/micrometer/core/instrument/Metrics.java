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

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class Metrics {
    public static final CompositeMeterRegistry globalRegistry = new CompositeMeterRegistry();

    public static void addRegistry(MeterRegistry registry) {
        globalRegistry.add(registry);
    }

    public static void removeRegistry(MeterRegistry registry) {
        globalRegistry.remove(registry);
    }

    /**
     * Tracks a monotonically increasing value.
     */
    public static Counter counter(Meter.Id id) {
        return globalRegistry.counter(id);
    }

    /**
     * Tracks a monotonically increasing value.
     */
    public static Counter counter(String name, Iterable<Tag> tags) {
        return globalRegistry.counter(name, tags);
    }

    /**
     * Tracks a monotonically increasing value.
     */
    public static Counter counter(String name, String... tags) {
        return globalRegistry.counter(name, tags);
    }

    /**
     * Measures the sample distribution of events.
     */
    public static DistributionSummary summary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return globalRegistry.summary(id, histogram, quantiles);
    }

    /**
     * Measures the sample distribution of events.
     */
    public static DistributionSummary summary(String name, Iterable<Tag> tags) {
        return globalRegistry.summary(name, tags);
    }

    /**
     * Measures the sample distribution of events.
     */
    public static DistributionSummary summary(String name, String... tags) {
        return globalRegistry.summary(name, tags);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     */
    public static Timer timer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return globalRegistry.timer(id, histogram, quantiles);
    }

    /**
     * Measures the time taken for short tasks.
     */
    public static Timer timer(String name, Iterable<Tag> tags) {
        return globalRegistry.timer(name, tags);
    }

    /**
     * Measures the time taken for short tasks.
     */
    public static Timer timer(String name, String... tags) {
        return globalRegistry.timer(name, tags);
    }

    static class More {
        /**
         * Measures the time taken for long tasks.
         */
        public LongTaskTimer longTaskTimer(Meter.Id id) {
            return globalRegistry.more().longTaskTimer(id);
        }

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter whenever
         * the value is observed.
         */
        public <T> Meter counter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
            return globalRegistry.more().counter(id, obj, f);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         */
        public <T extends Number> Meter counter(Meter.Id id, T number) {
            return globalRegistry.more().counter(id, number);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base time unit.
         */
        public <T> Gauge timeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
            return globalRegistry.more().timeGauge(id, obj, fUnit, f);
        }
    }

    private static final More more = new More();

    /**
     * Access to less frequently used meter types and patterns.
     */
    public static More more() {
        return more;
    }

    public static Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return globalRegistry.register(id, type, measurements);
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code f} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code f} on the object should be thread safe.
     * <p>
     * If multiple gauges are registered with the same id, then the values will be aggregated and
     * the sum will be reported. For example, registering multiple gauges for active threads in
     * a thread pool with the same id would produce a value that is the overall number
     * of active threads. For other behaviors, manage it on the user side and avoid multiple
     * registrations.
     *
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param obj  Object used to compute a value.
     * @param f    Function that is applied on the value for the number.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        return globalRegistry.gauge(name, tags, obj, f);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return globalRegistry.gauge(name, tags, number);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T extends Number> T gauge(String name, T number) {
        return globalRegistry.gauge(name, number);
    }

    /**
     * Register a gauge that reports the value of the object.
     *
     * @param name Name of the gauge being registered.
     * @param obj  Object used to compute a value.
     * @param f    Function that is applied on the value for the number.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
        return globalRegistry.gauge(name, obj, f);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Collection#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the gauge being registered.
     * @param tags       Sequence of dimensions for breaking down the name.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return globalRegistry.gaugeCollectionSize(name, tags, collection);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param map  Thread-safe implementation of {@link Map} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return globalRegistry.gaugeMapSize(name, tags, map);
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code f} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code f} on the object should be thread safe.
     * <p>
     * If multiple gauges are registered with the same id, then the values will be aggregated and
     * the sum will be reported. For example, registering multiple gauges for active threads in
     * a thread pool with the same id would produce a value that is the overall number
     * of active threads. For other behaviors, manage it on the user side and avoid multiple
     * registrations.
     *
     * @param id  Id of the gauge being registered.
     * @param obj Object used to compute a value.
     * @param f   Function that is applied on the value for the number.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public static <T> Gauge gauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return globalRegistry.gauge(id, obj, f);
    }
}
