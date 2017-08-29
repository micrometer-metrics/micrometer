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

import io.micrometer.core.instrument.stats.hist.Histogram;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

import static io.micrometer.core.instrument.Tags.zip;
import static java.util.Collections.emptyList;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry to iterate
 * over the set of meters instrumenting your application, and then further iterate over each meter's metrics, generally
 * resulting in a time series in the metrics backend for each combination of metrics and dimensions.
 *
 * @author Jon Schneider
 */
public interface MeterRegistry {
    /**
     * @return The set of registered meters.
     */
    Collection<Meter> getMeters();

    interface Config {
        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         */
        Config commonTags(Iterable<Tag> tags);

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         */
        default Config commonTags(String... tags) {
            commonTags(zip(tags));
            return this;
        }

        /**
         * @return The list of common tags in use on this registry.
         */
        Iterable<Tag> commonTags();

        /**
         * Use the provided naming convention, overriding the default for your monitoring system.
         */
        Config namingConvention(NamingConvention convention);

        /**
         * @return The naming convention currently in use on this registry.
         */
        NamingConvention namingConvention();

        /**
         * @return The clock used to measure durations of timers and long task timers (and sometimes
         * influences publishing behavior).
         */
        Clock clock();

        /**
         * Use the provided base unit of time, overriding the default for your monitoring system.
         */
        Config baseTimeUnit(TimeUnit unit);

        /**
         * @return The base unit of time that time-based observations are scaled to before publishing
         * to the monitoring system.
         */
        TimeUnit baseTimeUnit();
    }

    /**
     * Access to configuration options for this registry.
     */
    Config config();

    interface Search {
        default Search tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        Search tags(Iterable<Tag> tags);

        Search value(Statistic statistic, double value);

        Optional<Timer> timer();
        Optional<Counter> counter();
        Optional<Gauge> gauge();
        Optional<DistributionSummary> summary();
        Optional<LongTaskTimer> longTaskTimer();
        Optional<Meter> meter();
        Collection<Meter> meters();
    }

    Search find(String name);

    /**
     * Build a new Counter, which is registered with this registry once {@link Counter.Builder#create()} is called.
     *
     * @param name The name of the counter (which is the only requirement for a new counter).
     * @return The builder.
     */
    Counter.Builder counterBuilder(String name);

    /**
     * Tracks a monotonically increasing value.
     */
    default Counter counter(String name, Iterable<Tag> tags) {
        return counterBuilder(name).tags(tags).create();
    }

    /**
     * Tracks a monotonically increasing value.
     */
    default Counter counter(String name, String... tags) {
        return counter(name, zip(tags));
    }

    /**
     * Build a new Distribution Summary, which is registered with this registry once {@link DistributionSummary.Builder#create()} is called.
     *
     * @param name The name of the distribution summary (which is the only requirement for a new distribution summary).
     * @return The builder.
     */
    DistributionSummary.Builder summaryBuilder(String name);

    /**
     * Measures the sample distribution of events.
     */
    default DistributionSummary summary(String name, Iterable<Tag> tags) {
        return summaryBuilder(name).tags(tags).create();
    }

    /**
     * Measures the sample distribution of events.
     */
    default DistributionSummary summary(String name, String... tags) {
        return summary(name, zip(tags));
    }

    /**
     * Build a new Timer, which is registered with this registry once {@link Timer.Builder#create()} is called.
     *
     * @param name The name of the timer (which is the only requirement for a new timer).
     * @return The builder.
     */
    Timer.Builder timerBuilder(String name);

    /**
     * Measures the time taken for short tasks.
     */
    default Timer timer(String name, Iterable<Tag> tags) {
        return timerBuilder(name).tags(tags).create();
    }

    /**
     * Measures the time taken for short tasks.
     */
    default Timer timer(String name, String... tags) {
        return timer(name, zip(tags));
    }

    interface More {
        /**
         * Measures the time taken for short tasks.
         */
        default LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return longTaskTimerBuilder(name).tags(tags).create();
        }

        /**
         * Measures the time taken for short tasks.
         */
        default LongTaskTimer longTaskTimer(String name, String... tags) {
            return longTaskTimer(name, zip(tags));
        }

        /**
         * Build a new LongTaskTimer, which is registered with this registry once {@link LongTaskTimer.Builder#create()} is called.
         *
         * @param name The name of the timer (which is the only requirement for a new timer).
         * @return The builder.
         */
        LongTaskTimer.Builder longTaskTimerBuilder(String name);

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter whenever
         * the value is observed.
         */
        <T> T counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f);

        /**
         * Tracks a number, maintaining a weak reference on it.
         */
        default <T extends Number> T counter(String name, Iterable<Tag> tags, T number) {
            return counter(name, tags, number, Number::doubleValue);
        }
    }

    /**
     * Access to less frequently used meter types and patterns.
     */
    More more();

    /**
     * Register a custom meter type.
     * @param name Name of the meter being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param type Meter type, which may be used by naming conventions to normalize the name.
     * @param measurements A sequence of measurements describing how to sample the meter.
     * @return The registry.
     */
    MeterRegistry register(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements);

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
    default <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        gaugeBuilder(name, obj, f).tags(tags).create();
        return obj;
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
    default <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return gauge(name, tags, number, Number::doubleValue);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Number> T gauge(String name, T number) {
        return gauge(name, emptyList(), number);
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
    default <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
        return gauge(name, emptyList(), obj, f);
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
    default <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Collection::size);
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
    default <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return gauge(name, tags, map, Map::size);
    }

    /**
     * Build a new Gauge, which is registered with this registry once {@link Gauge.Builder#create()} is called.
     *
     * @param name The name of the gauge.
     * @param obj  Object used to compute a value.
     * @param f    Function that is applied on the value for the number.
     * @return The builder.
     */
    <T> Gauge.Builder gaugeBuilder(String name, T obj, ToDoubleFunction<T> f);

    /**
     * Begin to build a new Histogram.
     *
     * @return A histogram configuration used to build new histograms.
     */
    Histogram.Config histogram();
}
