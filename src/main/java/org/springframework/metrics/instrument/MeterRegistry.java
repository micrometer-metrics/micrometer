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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import org.springframework.metrics.instrument.internal.MonitoredExecutorService;
import org.springframework.metrics.instrument.binder.MeterBinder;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.springframework.metrics.instrument.Tags.tagList;

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

    default <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, String... tags) {
        return findMeter(mClass, name, Tags.tagList(tags));
    }

    default <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Stream<Tag> tags) {
        return findMeter(mClass, name, tags);
    }

    <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags);

    Clock getClock();

    /**
     * Measures the rate of some activity.
     */
    Counter counter(String name, Iterable<Tag> tags);

    /**
     * Measures the rate of some activity.
     */
    default Counter counter(String name, Stream<Tag> tags) {
        return counter(name, tags.collect(toList()));
    }

    /**
     * Measures the rate of some activity.
     */
    default Counter counter(String name) {
        return counter(name, emptyList());
    }

    /**
     * Measures the rate of some activity.
     */
    default Counter counter(String name, String... tags) {
        return counter(name, tagList(tags));
    }

    /**
     * Measures the sample distribution of events.
     */
    DistributionSummary distributionSummary(String name, Iterable<Tag> tags);

    /**
     * Measures the sample distribution of events.
     */
    default DistributionSummary distributionSummary(String name, Stream<Tag> tags) {
        return distributionSummary(name, tags.collect(toList()));
    }

    /**
     * Measures the sample distribution of events.
     */
    default DistributionSummary distributionSummary(String name) {
        return distributionSummary(name, emptyList());
    }

    /**
     * Measures the sample distribution of events.
     */
    default DistributionSummary distributionSummary(String name, String... tags) {
        return distributionSummary(name, tagList(tags));
    }

    /**
     * Build a new Timer, which is registered with this registry once {@link Timer.Builder#create()} is called.
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
    default Timer timer(String name, Stream<Tag> tags) {
        return timer(name, tags.collect(toList()));
    }

    /**
     * Measures the time taken for short tasks.
     */
    default Timer timer(String name) {
        return timer(name, emptyList());
    }

    /**
     * Measures the time taken for short tasks.
     */
    default Timer timer(String name, String... tags) {
        return timer(name, tagList(tags));
    }

    /**
     * Measures the time taken for short tasks.
     */
    LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags);

    /**
     * Measures the time taken for short tasks.
     */
    default LongTaskTimer longTaskTimer(String name, Stream<Tag> tags) {
        return longTaskTimer(name, tags.collect(toList()));
    }

    /**
     * Measures the time taken for short tasks.
     */
    default LongTaskTimer longTaskTimer(String name) {
        return longTaskTimer(name, emptyList());
    }

    /**
     * Measures the time taken for short tasks.
     */
    default LongTaskTimer longTaskTimer(String name, String... tags) {
        return longTaskTimer(name, tagList(tags));
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
     * @param name Name of the metric being registered.
     * @param tags Sequence of dimensions for breaking down the getName.
     * @param obj  Object used to compute a value.
     * @param f    Function that is applied on the value for the number.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T> T gauge(String name, Stream<Tag> tags, T obj, ToDoubleFunction<T> f) {
        return gauge(name, tags.collect(toList()), obj, f);
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
     * @param name Name of the metric being registered.
     * @param tags Sequence of dimensions for breaking down the getName.
     * @param obj  Object used to compute a value.
     * @param f    Function that is applied on the value for the number.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f);

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the metric being registered.
     * @param tags   Sequence of dimensions for breaking down the getName.
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
     * @param name   Name of the metric being registered.
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
     * @param name Name of the metric being registered.
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
     * @param name       Name of the metric being registered.
     * @param tags       Sequence of dimensions for breaking down the getName.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Collection<?>> T collectionSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Collection::size);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Collection#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the metric being registered.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Collection<?>> T collectionSize(String name, T collection) {
        return collectionSize(name, emptyList(), collection);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the metric being registered.
     * @param tags       Sequence of dimensions for breaking down the getName.
     * @param collection Thread-safe implementation of {@link Map} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Map<?, ?>> T mapSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Map::size);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the metric being registered.
     * @param collection Thread-safe implementation of {@link Map} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Map<?, ?>> T mapSize(String name, T collection) {
        return mapSize(name, emptyList(), collection);
    }

    /**
     * Execute an algorithm to bind one or more metrics to the registry.
     */
    default MeterRegistry bind(MeterBinder... binders) {
        for (MeterBinder binder : binders) {
            binder.bindTo(this);
        }
        return this;
    }

    /**
     * Record metrics on Guava caches.
     *
     * @param name  The name prefix of the metrics.
     * @param tags  Tags to apply to all recorded metrics.
     * @param cache The cache to instrument.
     * @return The instrumented cache, unchanged. The original cache is not
     * wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    default Cache monitor(String name, Iterable<Tag> tags, Cache cache) {
        return monitor(name, stream(tags.spliterator(), false), cache);
    }

    /**
     * Record metrics on Guava caches.
     *
     * @param name  The name prefix of the metrics.
     * @param tags  Tags to apply to all recorded metrics.
     * @param cache The cache to instrument.
     * @return The instrumented cache, unchanged. The original cache is not
     * wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    default Cache monitor(String name, Stream<Tag> tags, Cache cache) {
        CacheStats stats = cache.stats();

        gauge(name + "_size", tags, cache, Cache::size);
        gauge(name + "_hit_total", tags, stats, CacheStats::hitCount);
        gauge(name + "_miss_total", tags, stats, CacheStats::missCount);
        gauge(name + "_requests_total", tags, stats, CacheStats::requestCount);
        gauge(name + "_eviction_total", tags, stats, CacheStats::evictionCount);
        gauge(name + "_load_duration", tags, stats, CacheStats::totalLoadTime);

        if(cache instanceof LoadingCache) {
            gauge(name + "_loads_total", tags, stats, CacheStats::loadCount);
            gauge(name + "_load_failure_total", tags, stats, CacheStats::loadExceptionCount);
        }

        return cache;
    }

    /**
     * Record metrics on Guava caches.
     *
     * @param name  The name prefix of the metrics.
     * @param cache The cache to instrument.
     * @return The instrumented cache, unchanged. The original cache is not
     * wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    default Cache monitor(String name, Cache cache) {
        return monitor(name, emptyList(), cache);
    }

    /**
     * Record metrics on active connections and connection pool utilization.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param dataSource The data source to instrument.
     * @return The instrumented data source, unchanged. The original data source
     * is not wrapped or proxied in any way.
     */
    default DataSource monitor(String name, Iterable<Tag> tags, DataSource dataSource) {
        return monitor(name, stream(tags.spliterator(), false), dataSource);
    }

    /**
     * Record metrics on active connections and connection pool utilization. How
     * metadata regarding the data source's underlying pool and its utilization is
     * implementation-dependent.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param dataSource The data source to instrument.
     * @return The instrumented data source, unchanged. The original data source
     * is not wrapped or proxied in any way.
     */
    DataSource monitor(String name, Stream<Tag> tags, DataSource dataSource);

    /**
     * Record metrics on active connections and connection pool utilization.
     *
     * @param name       The name prefix of the metrics
     * @param dataSource The data source to instrument.
     * @return The instrumented data source, unchanged. The original data source
     * is not wrapped or proxied in any way.
     */
    default DataSource monitor(String name, DataSource dataSource) {
        return monitor(name, emptyList(), dataSource);
    }

    /**
     * Record metrics on the use of an executor.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default Executor monitor(String name, Iterable<Tag> tags, Executor executor) {
        return monitor(name, stream(tags.spliterator(), false), executor);
    }

    /**
     * Record metrics on the use of an executor.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default Executor monitor(String name, Stream<Tag> tags, Executor executor) {
        final Timer timer = timer(name, tags);
        return command -> timer.record(() -> executor.execute(command));
    }

    /**
     * Record metrics on the use of an executor service.
     *
     * @param name       The name prefix of the metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default Executor monitor(String name, Executor executor) {
        return monitor(name, emptyList(), executor);
    }

    /**
     * Record metrics on the use of an executor.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default ExecutorService monitor(String name, Stream<Tag> tags, ExecutorService executor) {
        return monitor(name, tags.collect(toList()), executor);
    }

    /**
     * Record metrics on the use of an executor.
     *
     * @param name       The name prefix of the metrics.
     * @param tags       Tags to apply to all recorded metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default ExecutorService monitor(String name, Iterable<Tag> tags, ExecutorService executor) {
        return new MonitoredExecutorService(this, executor, name, tags);
    }

    /**
     * Record metrics on the use of an executor.
     *
     * @param name       The name prefix of the metrics.
     * @param executor   The executor to instrument.
     * @return The instrumented executor, proxied.
     */
    default ExecutorService monitor(String name, ExecutorService executor) {
        return monitor(name, emptyList(), executor);
    }
}
