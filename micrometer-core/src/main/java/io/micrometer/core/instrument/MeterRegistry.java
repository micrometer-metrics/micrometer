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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.noop.*;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.Tags.zip;
import static java.util.Collections.emptyList;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry to iterate
 * over the set of meters instrumenting your application, and then further iterate over each meter's metrics, generally
 * resulting in a time series in the metrics backend for each combination of metrics and dimensions.
 * <p>
 * MeterRegistry may be used in a reactive context. As such, implementations must not negatively impact the calling
 * thread, e.g. it should respond immediately by avoiding IO call, deep stack recursion or any coordination.
 *
 * @author Jon Schneider
 */
public abstract class MeterRegistry {
    protected final Clock clock;

    protected MeterRegistry(Clock clock) {
        this.clock = clock;
    }

    private final Map<Id, Meter> meterMap = new HashMap<>();
    private final List<MeterFilter> filters = new ArrayList<>();

    /**
     * We'll use snake case as a general-purpose default for registries because it is the most
     * likely to result in a portable name. Camel casing is also perfectly acceptable. '-' and '.'
     * separators can pose problems for some monitoring systems. '-' is interpreted as metric
     * subtraction in some (including Prometheus), and '.' is used to flatten tags into hierarchical
     * names when shipping metrics to hierarchical backends such as Graphite.
     */
    private NamingConvention namingConvention = NamingConvention.snakeCase;

    protected abstract <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f);

    protected abstract Counter newCounter(Meter.Id id);

    protected abstract LongTaskTimer newLongTaskTimer(Meter.Id id);

    protected abstract Timer newTimer(Meter.Id id, HistogramConfig histogramConfig);

    protected abstract DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig);

    protected abstract void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements);

    protected <T> TimeGauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        TimeUnit baseTimeUnit = getBaseTimeUnit();
        id.setBaseUnit(getBaseTimeUnitStr());
        Gauge gauge = newGauge(id, obj, obj2 -> TimeUtils.convert(f.applyAsDouble(obj2), fUnit, getBaseTimeUnit()));

        return new TimeGauge() {
            @Override
            public Id getId() {
                return id;
            }

            @Override
            public double value() {
                return gauge.value();
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return baseTimeUnit;
            }
        };
    }

    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        FunctionTimer ft = new DefaultFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, getBaseTimeUnit());
        newMeter(id, Meter.Type.Timer, ft.measure());
        return ft;
    }

    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(config().namingConvention());
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(config().namingConvention());
    }

    protected abstract TimeUnit getBaseTimeUnit();

    private String getBaseTimeUnitStr() {
        if (getBaseTimeUnit() == null) {
            return null;
        }
        return getBaseTimeUnit().toString().toLowerCase();
    }

    Counter counter(Meter.Id id) {
        return registerMeterIfNecessary(Counter.class, id, this::newCounter, NoopCounter::new);
    }

    <T> Gauge gauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return registerMeterIfNecessary(Gauge.class, id, id2 -> newGauge(id2, obj, f), NoopGauge::new);
    }

    Timer timer(Meter.Id id, HistogramConfig histogramConfig) {
        return registerMeterIfNecessary(Timer.class, id, histogramConfig, (id2, filteredConfig) ->
            newTimer(id2, filteredConfig.merge(HistogramConfig.DEFAULT)), NoopTimer::new);
    }

    DistributionSummary summary(Meter.Id id, HistogramConfig histogramConfig) {
        return registerMeterIfNecessary(DistributionSummary.class, id, histogramConfig, (id2, filteredConfig) -> newDistributionSummary(id2, filteredConfig.merge(HistogramConfig.DEFAULT)), NoopDistributionSummary::new);
    }

    /**
     * Register a custom meter type.
     *
     * @param id           Id of the meter being registered.
     * @param type         Meter type, which may be used by naming conventions to normalize the name.
     * @param measurements A sequence of measurements describing how to sample the meter.
     * @return The registry.
     */
    Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return registerMeterIfNecessary(Meter.class, id, id2 -> {
            newMeter(id2, type, measurements);
            return new Meter() {
                @Override
                public Id getId() {
                    return id2;
                }

                @Override
                public Type type() {
                    return type;
                }

                @Override
                public Iterable<Measurement> measure() {
                    return measurements;
                }
            };
        }, NoopMeter::new);
    }

    /**
     * @return The set of registered meters.
     */
    public List<Meter> getMeters() {
        synchronized (meterMap) {
            return Collections.unmodifiableList(new ArrayList<>(meterMap.values()));
        }
    }

    public void forEachMeter(Consumer<? super Meter> consumer) {
        synchronized (meterMap) {
            meterMap.values().forEach(consumer);
        }
    }

    /**
     * Access to configuration options for this registry.
     */
    public class Config {
        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         */
        public Config commonTags(Iterable<Tag> tags) {
            meterFilter(MeterFilter.commonTags(tags));
            return this;
        }

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         * Must be an even number of arguments representing key/value pairs of tags.
         */
        public Config commonTags(String... tags) {
            return commonTags(zip(tags));
        }

        @Incubating(since = "1.0.0-rc.3")
        public Config meterFilter(MeterFilter filter) {
            filters.add(filter);
            return this;
        }

        /**
         * Use the provided naming convention, overriding the default for your monitoring system.
         */
        public Config namingConvention(NamingConvention convention) {
            namingConvention = convention;
            return this;
        }

        /**
         * @return The naming convention currently in use on this registry.
         */
        public NamingConvention namingConvention() {
            return namingConvention;
        }

        /**
         * @return The clock used to measure durations of timers and long task timers (and sometimes
         * influences publishing behavior).
         */
        public Clock clock() {
            return clock;
        }
    }

    private final Config config = new Config();

    public Config config() {
        return config;
    }

    public class Search {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        private final Map<Statistic, Double> valueAsserts = new EnumMap<>(Statistic.class);

        Search(String name) {
            this.name = name;
        }

        public Search tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Search tags(String... tags) {
            return tags(zip(tags));
        }

        public Search value(Statistic statistic, double value) {
            valueAsserts.put(statistic, value);
            return this;
        }

        public Optional<Timer> timer() {
            return findAny(Timer.class);
        }

        public Optional<Counter> counter() {
            return findAny(Counter.class);
        }

        public Optional<Gauge> gauge() {
            return findAny(Gauge.class);
        }

        public Optional<FunctionCounter> functionCounter() {
            return findAny(FunctionCounter.class);
        }

        public Optional<TimeGauge> timeGauge() {
            return findAny(TimeGauge.class);
        }

        public Optional<FunctionTimer> functionTimer() {
            return findAny(FunctionTimer.class);
        }

        public Optional<DistributionSummary> summary() {
            return findAny(DistributionSummary.class);
        }

        public Optional<LongTaskTimer> longTaskTimer() {
            return findAny(LongTaskTimer.class);
        }

        private <T> Optional<T> findAny(Class<T> clazz) {
            return meters()
                .stream()
                .filter(m -> clazz.isInstance(m))
                .findAny()
                .map(clazz::cast);
        }

        public Optional<Meter> meter() {
            return meters().stream().findAny();
        }

        public Collection<Meter> meters() {
            synchronized (meterMap) {
                Stream<Entry<Id, Meter>> entryStream =
                        meterMap.entrySet().stream().filter(e -> e.getKey().getName().equals(name));

                if (!tags.isEmpty()) {
                    entryStream = entryStream.filter(e -> {
                        final List<Tag> idTags = new ArrayList<>();
                        e.getKey().getTags().forEach(idTags::add);
                        return idTags.containsAll(tags);
                    });
                }

                Stream<Meter> meterStream = entryStream.map(Map.Entry::getValue);
                if (!valueAsserts.isEmpty()) {
                    meterStream = meterStream.filter(m -> {
                        for (Measurement measurement : m.measure()) {
                            final Double value = valueAsserts.get(measurement.getStatistic());
                            if (value != null && Math.abs(value - measurement.getValue()) > 1e-7) {
                                return false;
                            }
                        }
                        return true;
                    });
                }

                return meterStream.collect(Collectors.toList());
            }
        }
    }

    public Search find(String name) {
        return new Search(name);
    }

    /**
     * Tracks a monotonically increasing value.
     */
    public Counter counter(String name, Iterable<Tag> tags) {
        return Counter.builder(name).tags(tags).register(this);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     */
    public Counter counter(String name, String... tags) {
        return counter(name, zip(tags));
    }

    /**
     * Measures the sample distribution of events.
     */
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return DistributionSummary.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the sample distribution of events.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     */
    public DistributionSummary summary(String name, String... tags) {
        return summary(name, zip(tags));
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     */
    public Timer timer(String name, Iterable<Tag> tags) {
        return Timer.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     */
    public Timer timer(String name, String... tags) {
        return timer(name, zip(tags));
    }

    public class More {
        /**
         * Measures the time taken for long tasks.
         */
        public LongTaskTimer longTaskTimer(String name, String... tags) {
            return longTaskTimer(name, zip(tags));
        }

        /**
         * Measures the time taken for long tasks.
         */
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return LongTaskTimer.builder(name).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Only used by {@link LongTaskTimer#builder(String)}
         */
        LongTaskTimer longTaskTimer(Meter.Id id) {
            return registerMeterIfNecessary(LongTaskTimer.class, id, id2 -> {
                id2.setBaseUnit(getBaseTimeUnitStr());
                return newLongTaskTimer(id2);
            }, NoopLongTaskTimer::new);
        }

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter whenever
         * the value is observed.
         */
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
            return FunctionCounter.builder(name, obj, f).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         */
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return FunctionCounter.builder(name, number, Number::doubleValue).tags(tags).register(MeterRegistry.this);
        }

        <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
            WeakReference<T> ref = new WeakReference<>(obj);
            return registerMeterIfNecessary(FunctionCounter.class, id, id2 -> {
                FunctionCounter fc = new FunctionCounter() {
                    private volatile double last;

                    @Override
                    public double count() {
                        T obj2 = ref.get();
                        return obj2 != null ? (last = f.applyAsDouble(obj2)) : last;
                    }

                    @Override
                    public Id getId() {
                        return id2;
                    }
                };
                newMeter(id2, Meter.Type.Counter, fc.measure());
                return fc;
            }, NoopFunctionCounter::new);
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         */
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj,
                                       ToLongFunction<T> countFunction,
                                       ToDoubleFunction<T> totalTimeFunction,
                                       TimeUnit totalTimeFunctionUnits) {
            return FunctionTimer.builder(name, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits)
                .tags(tags).register(MeterRegistry.this);
        }

        <T> FunctionTimer timer(Meter.Id id, T obj,
                                ToLongFunction<T> countFunction,
                                ToDoubleFunction<T> totalTimeFunction,
                                TimeUnit totalTimeFunctionUnits) {
            return registerMeterIfNecessary(FunctionTimer.class, id, id2 -> {
                id2.setBaseUnit(getBaseTimeUnitStr());
                return newFunctionTimer(id2, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
            }, NoopFunctionTimer::new);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base time unit.
         */
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj,
                                       TimeUnit fUnit, ToDoubleFunction<T> f) {
            return TimeGauge.builder(name, obj, fUnit, f).tags(tags).register(MeterRegistry.this);
        }

        <T> TimeGauge timeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
            return registerMeterIfNecessary(TimeGauge.class, id, id2 -> newTimeGauge(id2, obj, fUnit, f), NoopTimeGauge::new);
        }
    }

    private final More more = new More();

    /**
     * Access to less frequently used meter types and patterns.
     */
    public More more() {
        return more;
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
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        Gauge.builder(name, obj, f).tags(tags).register(this);
        return obj;
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return gauge(name, tags, number, Number::doubleValue);
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public <T extends Number> T gauge(String name, T number) {
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
    public <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
        return gauge(name, emptyList(), obj, f);
    }

    /**
     * Register a gauge that reports the size of the {@link Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link Collection#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the gauge being registered.
     * @param tags       Sequence of dimensions for breaking down the name.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Collection::size);
    }

    /**
     * Register a gauge that reports the size of the {@link Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param map  Thread-safe implementation of {@link Map} used to access the value.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return gauge(name, tags, map, Map::size);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id, Function<Meter.Id, Meter> builder,
                                                         Function<Meter.Id, M> noopBuilder) {
        return registerMeterIfNecessary(meterClass, id, null, (id2, conf) -> builder.apply(id2), noopBuilder);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id, HistogramConfig config, BiFunction<Meter.Id, HistogramConfig, Meter> builder,
                                                         Function<Meter.Id, M> noopBuilder) {
        Meter.Id mappedId = id;
        for (MeterFilter filter : filters) {
            mappedId = filter.map(mappedId);
        }

        if (!accept(id)) {
            //noinspection unchecked
            return noopBuilder.apply(id);
        }

        if (config != null) {
            for (MeterFilter filter : filters) {
                HistogramConfig filteredConfig = filter.configure(mappedId, config);
                if (filteredConfig != null) {
                    config = filteredConfig;
                }
            }
        }

        // We cannot use ConcurrentHashMap.computeIfAbsent because of https://bugs.openjdk.java.net/browse/JDK-8062841
        // It is possible for the registration of meters to cause a secondary registration of other meters, such as
        // when a registry implementation chooses to register timer percentiles as separate gauges.

        // Furthermore, we cannot avoid synchronization to check for the existence of a meter first because double-checked
        // locking is unsafe unless the Meter implementation is strictly immutable, and that would be a difficult thing to
        // guarantee in user-provided implementations: http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html
        Meter m;
        synchronized (meterMap) {
            m = meterMap.get(mappedId);
            if (m == null) {
                m = builder.apply(mappedId, config);
                meterMap.put(mappedId, m);
            }
        }

        if (!meterClass.isInstance(m)) {
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }

        //noinspection unchecked
        return (M) m;
    }

    private boolean accept(Meter.Id id) {
        for (MeterFilter filter : filters) {
            switch (filter.accept(id)) {
                case DENY:
                    return false;
                case ACCEPT:
                    return true;
            }
        }

        return true;
    }
}
