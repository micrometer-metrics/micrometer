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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.springframework.metrics.instrument.Tags.zip;

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
        return findMeter(mClass, name, zip(tags));
    }

    <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags);

    default Optional<Meter> findMeter(Meter.Type type, String name, String... tags) {
        return findMeter(type, name, zip(tags));
    }

    Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags);

    Clock getClock();

    /**
     * Measures the rate of some activity.
     */
    Counter counter(String name, Iterable<Tag> tags);

    /**
     * Measures the rate of some activity.
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

    /**
     * Measures the time taken for short tasks.
     */
    LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags);

    /**
     * Measures the time taken for short tasks.
     */
    default LongTaskTimer longTaskTimer(String name, String... tags) {
        return longTaskTimer(name, zip(tags));
    }

    MeterRegistry register(Meter meter);

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
     * @param name   Name of the gauge being registered.
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
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @param name       Name of the gauge being registered.
     * @param tags       Sequence of dimensions for breaking down the getName.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Collection<?>> T collectionSize(T collection, String name, Iterable<Tag> tags) {
        return gauge(name, tags, collection, Collection::size);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Collection#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the gauge being registered.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @param tags       Tags to apply to the gauge.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Collection<?>> T collectionSize(T collection, String name, Tag... tags) {
        return collectionSize(collection, name, asList(tags));
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the getName.
     * @param map  Thread-safe implementation of {@link Map} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Map<?, ?>> T mapSize(T map, String name, Iterable<Tag> tags) {
        return gauge(name, tags, map, Map::size);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param map  Thread-safe implementation of {@link Map} used to access the value.
     * @param name Name of the gauge being registered.
     * @param tags Tags to apply to the gauge.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    default <T extends Map<?, ?>> T mapSize(T map, String name, String... tags) {
        return mapSize(map, name, zip(tags));
    }
}
