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

import io.micrometer.core.instrument.histogram.StatsConfig;
import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.Tags.zip;
import static java.util.Collections.emptyList;
import static java.util.stream.StreamSupport.stream;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry to iterate
 * over the set of meters instrumenting your application, and then further iterate over each meter's metrics, generally
 * resulting in a time series in the metrics backend for each combination of metrics and dimensions.
 *
 * MeterRegistry may be used in a reactive context. As such, implementations must not negatively impact the calling
 * thread, e.g. it should respond immediately by avoiding IO call, deep stack recursion or any coordination.
 *
 * @author Jon Schneider
 */
public abstract class MeterRegistry {
    protected final Clock clock;

    public MeterRegistry(Clock clock) {
        this.clock = clock;
    }

    /**
     * List of common tags to append to every metric, stored pre-formatted.
     */
    private final List<Tag> commonTags = new ArrayList<>();

    private final Map<Meter.Id, Meter> meterMap = new HashMap<>();

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

    protected abstract Timer newTimer(Meter.Id id, StatsConfig statsConfig);

    protected abstract DistributionSummary newDistributionSummary(Meter.Id id, StatsConfig statsConfig);

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
            public TimeUnit getBaseTimeUnit() {
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
        if(getBaseTimeUnit() == null)
            return null;
        return getBaseTimeUnit().toString().toLowerCase();
    }

    Counter counter(Meter.Id id) {
        return registerMeterIfNecessary(Counter.class, id, id2 -> {
            id2.setType(Meter.Type.Counter);
            return newCounter(id2);
        });
    }

    <T> Gauge gauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return registerMeterIfNecessary(Gauge.class, id, id2 -> {
            id2.setType(Meter.Type.Gauge);
            return newGauge(id2, obj, f);
        });
    }

    Timer timer(Meter.Id id, StatsConfig statsConfig) {
        return registerMeterIfNecessary(Timer.class, id, id2 -> {
            id2.setType(Meter.Type.Timer);
            return newTimer(id2, statsConfig);
        });
    }

    DistributionSummary summary(Meter.Id id, StatsConfig statsConfig) {
        return registerMeterIfNecessary(DistributionSummary.class, id, id2 -> {
            id2.setType(Meter.Type.DistributionSummary);
            return newDistributionSummary(id2, statsConfig);
        });
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
            id2.setType(type);
            newMeter(id2, type, measurements);
            return new Meter() {
                @Override
                public Id getId() {
                    return id2;
                }

                @Override
                public Type getType() {
                    return type;
                }

                @Override
                public Iterable<Measurement> measure() {
                    return measurements;
                }
            };
        });
    }

    // ---------------------

    /**
     * @return The set of registered meters.
     */
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    /**
     * Access to configuration options for this registry.
     */
    public class Config {
        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         */
        public Config commonTags(Iterable<Tag> tags) {
            stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .forEach(commonTags::add);
            return this;
        }

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         * Must be an even number of arguments representing key/value pairs of tags.
         */
        public Config commonTags(String... tags) {
            commonTags(zip(tags));
            return this;
        }

        /**
         * @return The list of common tags in use on this registry.
         */
        public Iterable<Tag> commonTags() {
            return commonTags;
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
        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        private final String name;
        private List<Tag> tags = new ArrayList<>();
        private Map<Statistic, Double> valueAsserts = new HashMap<>();

        Search(String name) {
            this.name = name;
        }

        public Search tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Search tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Search value(Statistic statistic, double value) {
            valueAsserts.put(statistic, value);
            return this;
        }

        public Optional<Timer> timer() {
            return meters()
                .stream()
                .filter(m -> m instanceof Timer)
                .findAny()
                .map(Timer.class::cast);
        }

        public Optional<Counter> counter() {
            return meters()
                .stream()
                .filter(m -> m instanceof Counter)
                .findAny()
                .map(Counter.class::cast);
        }

        public Optional<Gauge> gauge() {
            return meters()
                .stream()
                .filter(m -> m instanceof Gauge)
                .findAny()
                .map(Gauge.class::cast);
        }

        public Optional<DistributionSummary> summary() {
            return meters()
                .stream()
                .filter(m -> m instanceof DistributionSummary)
                .findAny()
                .map(DistributionSummary.class::cast);
        }

        public Optional<LongTaskTimer> longTaskTimer() {
            return meters()
                .stream()
                .filter(m -> m instanceof LongTaskTimer)
                .findAny()
                .map(LongTaskTimer.class::cast);
        }

        public Optional<Meter> meter() {
            return meters().stream().findAny();
        }

        public Collection<Meter> meters() {
            synchronized (meterMap) {
                return meterMap.keySet().stream()
                    .filter(id -> id.getName().equals(name))
                    .filter(id -> {
                        if (tags.isEmpty())
                            return true;
                        List<Tag> idTags = new ArrayList<>();
                        id.getTags().forEach(idTags::add);
                        return idTags.containsAll(tags);
                    })
                    .map(meterMap::get)
                    .filter(m -> {
                        if (valueAsserts.isEmpty())
                            return true;
                        for (Measurement measurement : m.measure()) {
                            if (valueAsserts.containsKey(measurement.getStatistic()) &&
                                Math.abs(valueAsserts.get(measurement.getStatistic()) - measurement.getValue()) > 1e-7) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
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
        return counter(createId(name, tags, null));
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
        return summary(createId(name, tags, null), new StatsConfig());
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
        return timer(createId(name, tags, null), new StatsConfig());
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
            return longTaskTimer(name, Tags.zip(tags));
        }

        /**
         * Measures the time taken for long tasks.
         */
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return longTaskTimer(createId(name, tags, null));
        }

        /**
         * Only used by {@link LongTaskTimer#builder(String)}
         */
        LongTaskTimer longTaskTimer(Meter.Id id) {
            return registerMeterIfNecessary(LongTaskTimer.class, id, id2 -> {
                id2.setType(Meter.Type.LongTaskTimer);
                id2.setBaseUnit(getBaseTimeUnitStr());
                return newLongTaskTimer(id2);
            });
        }

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter whenever
         * the value is observed.
         */
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
            return counter(createId(name, tags, null), obj, f);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         */
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return counter(createId(name, tags, null), number, Number::doubleValue);
        }

        <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
            WeakReference<T> ref = new WeakReference<>(obj);

            return registerMeterIfNecessary(FunctionCounter.class, id, id2 -> {
                id2.setType(Meter.Type.Counter);
                FunctionCounter fc = new FunctionCounter() {
                    private volatile double last = 0.0;

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
            });
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         */
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj,
                                ToLongFunction<T> countFunction,
                                ToDoubleFunction<T> totalTimeFunction,
                                TimeUnit totalTimeFunctionUnits) {
            return timer(createId(name, tags, null), obj, countFunction,
                totalTimeFunction, totalTimeFunctionUnits);
        }

        <T> FunctionTimer timer(Meter.Id id, T obj,
                                ToLongFunction<T> countFunction,
                                ToDoubleFunction<T> totalTimeFunction,
                                TimeUnit totalTimeFunctionUnits) {
            return registerMeterIfNecessary(FunctionTimer.class, id, id2 -> {
                id2.setType(Meter.Type.Timer);
                id2.setBaseUnit(getBaseTimeUnitStr());
                return newFunctionTimer(id2, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
            });
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base time unit.
         */
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj,
                                   TimeUnit fUnit, ToDoubleFunction<T> f) {
            return timeGauge(createId(name, tags, null), obj, fUnit, f);
        }

        <T> TimeGauge timeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
            return registerMeterIfNecessary(TimeGauge.class, id, id2 -> {
                id2.setType(Meter.Type.Gauge);
                return newTimeGauge(id2, obj, fUnit, f);
            });
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
        gauge(createId(name, tags, null), obj, f);
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
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
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
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
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
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return gauge(name, tags, map, Map::size);
    }

    private Meter.Id createId(String name, Iterable<Tag> tags, String description) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name must be non-empty");
        }
        return new Meter.Id(name, tags, description, null);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id, Function<Meter.Id, Meter> builder) {
        // If the id is coming down from a composite registry it will already have the common tags of the composite.
        // This adds common tags of the registry within the composite.
        Meter.Id idWithCommonTags = new Meter.Id(id.getName(), Tags.concat(id.getTags(), config().commonTags()),
            id.getBaseUnit(), id.getDescription());

        Meter m = meterMap.get(idWithCommonTags);

        if (m == null) {
            m = builder.apply(idWithCommonTags);

            synchronized (meterMap) {
                Meter m2 = meterMap.putIfAbsent(idWithCommonTags, m);
                m = m2 == null ? m : m2;
            }
        }

        if (!meterClass.isInstance(m)) {
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }

        //noinspection unchecked
        return (M) m;
    }
}
