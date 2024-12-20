/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.*;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry
 * to iterate over the set of meters instrumenting your application, and then further
 * iterate over each meter's metrics, generally resulting in a time series in the metrics
 * backend for each combination of metrics and dimensions.
 * <p>
 * MeterRegistry may be used in a reactive context. As such, implementations must not
 * negatively impact the calling thread, e.g. it should respond immediately by avoiding IO
 * call, deep stack recursion or any coordination.
 * <p>
 * If you register meters having the same ID multiple times, the first registration only
 * will work and the subsequent registrations will be ignored.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
public abstract class MeterRegistry {

    private static final WarnThenDebugLogger gaugeDoubleRegistrationLogger = new WarnThenDebugLogger(
            MeterRegistry.class);

    // @formatter:off
    private static final EnumMap<TimeUnit, String> BASE_TIME_UNIT_STRING_CACHE = Arrays.stream(TimeUnit.values())
        .collect(
            Collectors.toMap(
                Function.identity(),
                (timeUnit) -> timeUnit.toString().toLowerCase(Locale.ROOT),
                (k, v) -> { throw new IllegalStateException("Duplicate keys should not exist."); },
                () -> new EnumMap<>(TimeUnit.class)
            )
        );
    // @formatter:on

    protected final Clock clock;

    private final Object meterMapLock = new Object();

    private volatile MeterFilter[] filters = new MeterFilter[0];

    private final List<Consumer<Meter>> meterAddedListeners = new CopyOnWriteArrayList<>();

    private final List<Consumer<Meter>> meterRemovedListeners = new CopyOnWriteArrayList<>();

    private final List<BiConsumer<Meter.Id, String>> meterRegistrationFailedListeners = new CopyOnWriteArrayList<>();

    private final Config config = new Config();

    private final More more = new More();

    /**
     * Even though writes are guarded by meterMapLock, iterators across value space are
     * supported. Hence, we use CHM to support that iteration without
     * ConcurrentModificationException risk.
     */
    private final Map<Id, Meter> meterMap = new ConcurrentHashMap<>();

    /**
     * write/remove guarded by meterMapLock, read in
     * {@link #getOrCreateMeter(DistributionStatisticConfig, BiFunction, Id, Function)} is
     * unguarded
     */
    private final Map<Id, Meter> preFilterIdToMeterMap = new HashMap<>();

    /**
     * For reverse looking up pre-filter ID in {@link #preFilterIdToMeterMap} from the
     * Meter being removed in {@link #remove(Id)}. Guarded by the {@link #meterMapLock}.
     */
    private final Map<Meter, Id> meterToPreFilterIdMap = new HashMap<>();

    /**
     * Only needed when MeterFilter configured after Meters registered. Write/remove
     * guarded by meterMapLock, remove in {@link #unmarkStaleId(Id)} and other operations
     * unguarded
     */
    private final Set<Id> stalePreFilterIds = new HashSet<>();

    /**
     * Map of meter id whose associated meter contains synthetic counterparts to those
     * synthetic ids. We maintain these associations so that when we remove a meter with
     * synthetics, they can removed as well.
     */
    // Guarded by meterMapLock for both reads and writes
    private final Map<Id, Set<Id>> syntheticAssociations = new HashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    private PauseDetector pauseDetector = new NoPauseDetector();

    @Nullable
    private HighCardinalityTagsDetector highCardinalityTagsDetector;

    /**
     * We'll use snake case as a general-purpose default for registries because it is the
     * most likely to result in a portable name. Camel casing is also perfectly
     * acceptable. '-' and '.' separators can pose problems for some monitoring systems.
     * '-' is interpreted as metric subtraction in some (including Prometheus), and '.' is
     * used to flatten tags into hierarchical names when shipping metrics to hierarchical
     * backends such as Graphite.
     */
    private NamingConvention namingConvention = NamingConvention.snakeCase;

    protected MeterRegistry(Clock clock) {
        requireNonNull(clock);
        this.clock = clock;
    }

    /**
     * Build a new gauge to be added to the registry. This is guaranteed to only be called
     * if the gauge doesn't already exist.
     * @param id The id that uniquely identifies the gauge.
     * @param obj State object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return A new gauge.
     */
    protected abstract <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction);

    /**
     * Build a new counter to be added to the registry. This is guaranteed to only be
     * called if the counter doesn't already exist.
     * @param id The id that uniquely identifies the counter.
     * @return A new counter.
     */
    protected abstract Counter newCounter(Meter.Id id);

    /**
     * Build a new long task timer to be added to the registry. This is guaranteed to only
     * be called if the long task timer doesn't already exist.
     * @param id The id that uniquely identifies the long task timer.
     * @return A new long task timer.
     * @deprecated Implement
     * {@link #newLongTaskTimer(Meter.Id, DistributionStatisticConfig)} instead.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        throw new UnsupportedOperationException("MeterRegistry implementations may still override this, but it is only "
                + "invoked by the overloaded form of newLongTaskTimer for backwards compatibility.");
    }

    /**
     * Build a new long task timer to be added to the registry. This is guaranteed to only
     * be called if the long task timer doesn't already exist.
     * @param id The id that uniquely identifies the long task timer.
     * @param distributionStatisticConfig Configuration for published distribution
     * statistics.
     * @return A new long task timer.
     * @since 1.5.0
     */
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return newLongTaskTimer(id); // default implementation for backwards compatibility
    }

    /**
     * Build a new timer to be added to the registry. This is guaranteed to only be called
     * if the timer doesn't already exist.
     * @param id The id that uniquely identifies the timer.
     * @param distributionStatisticConfig Configuration for published distribution
     * statistics.
     * @param pauseDetector The pause detector to use for coordinated omission
     * compensation.
     * @return A new timer.
     */
    protected abstract Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector);

    /**
     * Build a new distribution summary to be added to the registry. This is guaranteed to
     * only be called if the distribution summary doesn't already exist.
     * @param id The id that uniquely identifies the distribution summary.
     * @param distributionStatisticConfig Configuration for published distribution
     * statistics.
     * @param scale Multiply every recorded sample by this factor.
     * @return A new distribution summary.
     */
    protected abstract DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale);

    /**
     * Build a new custom meter to be added to the registry. This is guaranteed to only be
     * called if the custom meter doesn't already exist.
     * @param id The id that uniquely identifies the custom meter.
     * @param type What kind of meter this is.
     * @param measurements A set of measurements describing how to sample this meter.
     * @return A new custom meter.
     */
    protected abstract Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements);

    /**
     * Build a new time gauge to be added to the registry. This is guaranteed to only be
     * called if the time gauge doesn't already exist.
     * @param id The id that uniquely identifies the time gauge.
     * @param obj The state object from which the value function derives a measurement.
     * @param valueFunctionUnit The base unit of time returned by the value function.
     * @param valueFunction A function returning a time value that can go up or down.
     * @param <T> The type of the object upon which the value function derives a
     * measurement.
     * @return A new time gauge.
     */
    protected <T> TimeGauge newTimeGauge(Meter.Id id, @Nullable T obj, TimeUnit valueFunctionUnit,
            ToDoubleFunction<T> valueFunction) {
        Meter.Id withUnit = id.withBaseUnit(getBaseTimeUnitStr());
        Gauge gauge = newGauge(withUnit, obj,
                obj2 -> TimeUtils.convert(valueFunction.applyAsDouble(obj2), valueFunctionUnit, getBaseTimeUnit()));

        return new TimeGauge() {
            @Override
            public Id getId() {
                return withUnit;
            }

            @Override
            public double value() {
                return gauge.value();
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return getBaseTimeUnit();
            }
        };
    }

    /**
     * Build a new function timer to be added to the registry. This is guaranteed to only
     * be called if the function timer doesn't already exist.
     * @param id The id that uniquely identifies the function timer.
     * @param obj The state object from which the count and total functions derive
     * measurements.
     * @param countFunction A monotonically increasing count function.
     * @param totalTimeFunction A monotonically increasing total time function.
     * @param totalTimeFunctionUnit The base unit of time of the totals returned by the
     * total time function.
     * @param <T> The type of the object upon which the value functions derives their
     * measurements.
     * @return A new function timer.
     */
    protected abstract <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit);

    /**
     * Build a new function counter to be added to the registry. This is guaranteed to
     * only be called if the function counter doesn't already exist.
     * @param id The id that uniquely identifies the function counter.
     * @param obj The state object from which the count function derives a measurement.
     * @param countFunction A monotonically increasing count function.
     * @param <T> The type of the object upon which the value function derives a
     * measurement.
     * @return A new function counter.
     */
    protected abstract <T> FunctionCounter newFunctionCounter(Id id, T obj, ToDoubleFunction<T> countFunction);

    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(config().namingConvention());
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(config().namingConvention());
    }

    /**
     * @return the registry's base TimeUnit. Must not be null.
     */
    protected abstract TimeUnit getBaseTimeUnit();

    /**
     * Every custom registry implementation should define a default histogram expiry at a
     * minimum: <pre>
     * DistributionStatisticConfig.builder()
     *    .expiry(defaultStep)
     *    .build()
     *    .merge(DistributionStatisticConfig.DEFAULT);
     * </pre>
     * @return The default distribution statistics config.
     */
    protected abstract DistributionStatisticConfig defaultHistogramConfig();

    private String getBaseTimeUnitStr() {
        return BASE_TIME_UNIT_STRING_CACHE.get(getBaseTimeUnit());
    }

    /**
     * Only used by {@link Counter#builder(String)}.
     * @param id The identifier for this counter.
     * @return A new or existing counter.
     */
    Counter counter(Meter.Id id) {
        return registerMeterIfNecessary(Counter.class, id, this::newCounter, NoopCounter::new);
    }

    /**
     * Only used by {@link Gauge#builder(String, Object, ToDoubleFunction)}.
     * @param id The identifier for this gauge.
     * @param obj State object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return A new or existing gauge.
     */
    <T> Gauge gauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return registerMeterIfNecessary(Gauge.class, id, id2 -> newGauge(id2, obj, valueFunction), NoopGauge::new);
    }

    /**
     * Only used by {@link Timer#builder(String)}.
     * @param id The identifier for this timer.
     * @param distributionStatisticConfig Configuration that governs how distribution
     * statistics are computed.
     * @return A new or existing timer.
     */
    Timer timer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetectorOverride) {
        return registerMeterIfNecessary(Timer.class, id, distributionStatisticConfig, (id2, filteredConfig) -> {
            Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
            return newTimer(withUnit, filteredConfig.merge(defaultHistogramConfig()), pauseDetectorOverride);
        }, NoopTimer::new);
    }

    /**
     * Only used by {@link DistributionSummary#builder(String)}.
     * @param id The identifier for this distribution summary.
     * @param distributionStatisticConfig Configuration that governs how distribution
     * statistics are computed.
     * @return A new or existing distribution summary.
     */
    DistributionSummary summary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return registerMeterIfNecessary(DistributionSummary.class, id, distributionStatisticConfig, (id2,
                filteredConfig) -> newDistributionSummary(id2, filteredConfig.merge(defaultHistogramConfig()), scale),
                NoopDistributionSummary::new);
    }

    /**
     * Register a custom meter type.
     * @param id Id of the meter being registered.
     * @param type Meter type, which may be used by naming conventions to normalize the
     * name.
     * @param measurements A sequence of measurements describing how to sample the meter.
     * @return The meter.
     */
    Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return registerMeterIfNecessary(Meter.class, id, id2 -> newMeter(id2, type, measurements), NoopMeter::new);
    }

    /**
     * @return The set of registered meters.
     */
    public List<Meter> getMeters() {
        return Collections.unmodifiableList(new ArrayList<>(meterMap.values()));
    }

    /**
     * Iterate over each meter in the registry.
     * @param consumer Consumer of each meter during iteration.
     */
    public void forEachMeter(Consumer<? super Meter> consumer) {
        meterMap.values().forEach(consumer);
    }

    /**
     * @return A configuration object used to change the behavior of this registry.
     */
    public Config config() {
        return config;
    }

    /**
     * Initiate a search beginning with a metric name. If constraints added in the search
     * are not satisfied, the search will return {@code null}.
     * @param name The meter name to locate.
     * @return A new search.
     */
    public Search find(String name) {
        return Search.in(this).name(name);
    }

    /**
     * Initiate a search beginning with a metric name. All constraints added in the search
     * must be satisfied or an {@link MeterNotFoundException} is thrown.
     * @param name The meter name to locate.
     * @return A new search.
     */
    public RequiredSearch get(String name) {
        return RequiredSearch.in(this).name(name);
    }

    /**
     * Tracks a monotonically increasing value.
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing counter.
     */
    public Counter counter(String name, Iterable<Tag> tags) {
        return Counter.builder(name).tags(tags).register(this);
    }

    /**
     * Tracks a monotonically increasing value.
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of
     * tags.
     * @return A new or existing counter.
     */
    public Counter counter(String name, String... tags) {
        return counter(name, Tags.of(tags));
    }

    /**
     * Measures the distribution of samples.
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return DistributionSummary.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the distribution of samples.
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of
     * tags.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, String... tags) {
        return summary(name, Tags.of(tags));
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing timer.
     */
    public Timer timer(String name, Iterable<Tag> tags) {
        return Timer.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of
     * tags.
     * @return A new or existing timer.
     */
    public Timer timer(String name, String... tags) {
        return timer(name, Tags.of(tags));
    }

    /**
     * Access to less frequently used meter types and patterns.
     * @return Access to additional meter types and patterns.
     */
    public More more() {
        return more;
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code valueFunction} is applied. The registration will keep a weak reference to
     * the object so it will not prevent garbage collection. Applying
     * {@code valueFunction} on the object should be thread safe.
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param stateObject State object used to compute a value.
     * @param valueFunction Function that produces an instantaneous gauge value from the
     * state object.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return The state object that was passed in so the registration can be done as part
     * of an assignment statement.
     */
    @Nullable
    public <T> T gauge(String name, Iterable<Tag> tags, @Nullable T stateObject, ToDoubleFunction<T> valueFunction) {
        Gauge.builder(name, stateObject, valueFunction).tags(tags).register(this);
        return stateObject;
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the
     * value.
     * @param <T> The type of the number from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an
     * assignment statement.
     */
    @Nullable
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return gauge(name, tags, number, Number::doubleValue);
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     * @param name Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the
     * value.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an
     * assignment statement.
     */
    @Nullable
    public <T extends Number> T gauge(String name, T number) {
        return gauge(name, emptyList(), number);
    }

    /**
     * Register a gauge that reports the value of the object.
     * @param name Name of the gauge being registered.
     * @param stateObject State object used to compute a value.
     * @param valueFunction Function that produces an instantaneous gauge value from the
     * state object.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return The state object that was passed in so the registration can be done as part
     * of an assignment statement.
     */
    @Nullable
    public <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction) {
        return gauge(name, emptyList(), stateObject, valueFunction);
    }

    /**
     * Register a gauge that reports the size of the {@link Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage
     * collection. The collection implementation used should be thread safe. Note that
     * calling {@link Collection#size()} can be expensive for some collection
     * implementations and should be considered before registering.
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param collection Thread-safe implementation of {@link Collection} used to access
     * the value.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return The Collection that was passed in so the registration can be done as part
     * of an assignment statement.
     */
    @Nullable
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Collection::size);
    }

    /**
     * Register a gauge that reports the size of the {@link Map}. The registration will
     * keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link Map#size()} can be expensive for some collection implementations and should
     * be considered before registering.
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param map Thread-safe implementation of {@link Map} used to access the value.
     * @param <T> The type of the state object from which the gauge value is extracted.
     * @return The Map that was passed in so the registration can be done as part of an
     * assignment statement.
     */
    @Nullable
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return gauge(name, tags, map, Map::size);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id,
            Function<Meter.Id, M> builder, Function<Meter.Id, M> noopBuilder) {
        return registerMeterIfNecessary(meterClass, id, null, (id2, conf) -> builder.apply(id2), noopBuilder);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id,
            @Nullable DistributionStatisticConfig config, BiFunction<Meter.Id, DistributionStatisticConfig, M> builder,
            Function<Meter.Id, M> noopBuilder) {
        Meter m = getOrCreateMeter(config, builder, id, noopBuilder);

        if (!meterClass.isInstance(m)) {
            throw new IllegalArgumentException(
                    format("There is already a registered meter of a different type (%s vs. %s) with the same name: %s",
                            m.getClass().getSimpleName(), meterClass.getSimpleName(), id.getName()));
        }
        return meterClass.cast(m);
    }

    private Id getMappedId(Id id) {
        if (id.syntheticAssociation() != null) {
            return id;
        }
        Id mappedId = id;
        for (MeterFilter filter : filters) {
            mappedId = filter.map(mappedId);
        }
        return mappedId;
    }

    private Meter getOrCreateMeter(@Nullable DistributionStatisticConfig config,
            BiFunction<Id, /* Nullable Generic */ DistributionStatisticConfig, ? extends Meter> builder, Id originalId,
            Function<Meter.Id, ? extends Meter> noopBuilder) {

        Meter m = preFilterIdToMeterMap.get(originalId);
        if (m != null && !isStaleId(originalId)) {
            checkAndWarnAboutGaugeDoubleRegistration(m);
            return m;
        }

        Id mappedId = getMappedId(originalId);
        m = meterMap.get(mappedId);

        if (m != null) {
            // If the mapping exists and the meter is marked stale, then this meter is no
            // longer stale.
            if (isStaleId(originalId)) {
                unmarkStaleId(originalId);
            }
            checkAndWarnAboutGaugeDoubleRegistration(m);
        }
        else {
            if (isClosed()) {
                return noopBuilder.apply(mappedId);
            }

            synchronized (meterMapLock) {
                m = meterMap.get(mappedId);

                if (m == null) {
                    if (!accept(mappedId)) {
                        return noopBuilder.apply(mappedId);
                    }

                    if (config != null) {
                        for (MeterFilter filter : filters) {
                            DistributionStatisticConfig filteredConfig = filter.configure(mappedId, config);
                            if (filteredConfig != null) {
                                config = filteredConfig;
                            }
                        }
                    }

                    m = builder.apply(mappedId, config);

                    Id synAssoc = mappedId.syntheticAssociation();
                    if (synAssoc != null) {
                        Set<Id> associations = syntheticAssociations.computeIfAbsent(synAssoc, k -> new HashSet<>());
                        associations.add(mappedId);
                    }

                    for (Consumer<Meter> onAdd : meterAddedListeners) {
                        onAdd.accept(m);
                    }
                    meterMap.put(mappedId, m);
                    preFilterIdToMeterMap.put(originalId, m);
                    meterToPreFilterIdMap.put(m, originalId);
                    unmarkStaleId(originalId);
                }
            }
        }

        return m;
    }

    private boolean isStaleId(Id originalId) {
        return !stalePreFilterIds.isEmpty() && stalePreFilterIds.contains(originalId);
    }

    /**
     * Marks the ID as no longer stale if it is stale. Otherwise, does nothing.
     * @param originalId id before any filter mapping has been applied
     * @return {@code true} if the id is stale
     */
    private boolean unmarkStaleId(Id originalId) {
        return !stalePreFilterIds.isEmpty() && stalePreFilterIds.remove(originalId);
    }

    private void checkAndWarnAboutGaugeDoubleRegistration(Meter meter) {
        if (meter instanceof Gauge) {
            gaugeDoubleRegistrationLogger.log(() -> String.format(
                    "This Gauge has been already registered (%s), the Gauge registration will be ignored.",
                    meter.getId()));
        }
    }

    private boolean accept(Meter.Id id) {
        for (MeterFilter filter : filters) {
            MeterFilterReply reply = filter.accept(id);
            if (reply == MeterFilterReply.DENY) {
                return false;
            }
            else if (reply == MeterFilterReply.ACCEPT) {
                return true;
            }
        }
        return true;
    }

    /**
     * Remove a {@link Meter} from this {@link MeterRegistry registry}. This is expected
     * to be a {@link Meter} with the same {@link Id} returned when registering a meter -
     * which will have {@link MeterFilter}s applied to it.
     * @param meter The meter to remove
     * @return The removed meter, or null if the provided meter is not currently
     * registered.
     * @since 1.1.0
     */
    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter meter) {
        return remove(meter.getId());
    }

    /**
     * Remove a {@link Meter} from this {@link MeterRegistry registry} based on its
     * {@link Id} before applying this registry's {@link MeterFilter}s to the given
     * {@link Id}.
     * @param preFilterId the id of the meter to remove
     * @return The removed meter, or null if the meter is not found
     * @since 1.3.16
     */
    @Incubating(since = "1.3.16")
    @Nullable
    public Meter removeByPreFilterId(Meter.Id preFilterId) {
        final Meter meterToRemove = preFilterIdToMeterMap.get(preFilterId);
        if (meterToRemove == null)
            return remove(getMappedId(preFilterId));
        return remove(meterToRemove);
    }

    /**
     * Remove a {@link Meter} from this {@link MeterRegistry registry} based the given
     * {@link Id} as-is. The registry's {@link MeterFilter}s will not be applied to it.
     * You can use the {@link Id} of the {@link Meter} returned when registering a meter,
     * since that will have {@link MeterFilter}s already applied to it.
     * @param mappedId The id of the meter to remove
     * @return The removed meter, or null if no meter matched the provided id.
     * @since 1.1.0
     */
    @Incubating(since = "1.1.0")
    @Nullable
    public Meter remove(Meter.Id mappedId) {
        if (meterMap.containsKey(mappedId)) {
            synchronized (meterMapLock) {
                final Meter removedMeter = meterMap.remove(mappedId);
                if (removedMeter != null) {
                    Id preFilterIdToRemove = meterToPreFilterIdMap.remove(removedMeter);
                    preFilterIdToMeterMap.remove(preFilterIdToRemove);
                    stalePreFilterIds.remove(preFilterIdToRemove);

                    Set<Id> synthetics = syntheticAssociations.remove(mappedId);
                    if (synthetics != null) {
                        for (Id synthetic : synthetics) {
                            remove(synthetic);
                        }
                    }

                    for (Consumer<Meter> onRemove : meterRemovedListeners) {
                        onRemove.accept(removedMeter);
                    }

                    return removedMeter;
                }
            }
        }

        return null;
    }

    /**
     * Clear all meters.
     * @since 1.2.0
     */
    @Incubating(since = "1.2.0")
    public void clear() {
        meterMap.keySet().forEach(this::remove);
    }

    /**
     * Access to configuration options for this registry.
     */
    public class Config {

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring
         * system.
         * @param tags Tags to add to every metric.
         * @return This configuration instance.
         */
        public Config commonTags(Iterable<Tag> tags) {
            return meterFilter(MeterFilter.commonTags(tags));
        }

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring
         * system. Must be an even number of arguments representing key/value pairs of
         * tags.
         * @param tags MUST be an even number of arguments representing key/value pairs of
         * tags.
         * @return This configuration instance.
         */
        public Config commonTags(String... tags) {
            return commonTags(Tags.of(tags));
        }

        /**
         * Add a meter filter to the registry. Filters are applied in the order in which
         * they are added.
         * @param filter The filter to add to the registry.
         * @return This configuration instance.
         */
        public synchronized Config meterFilter(MeterFilter filter) {
            if (!meterMap.isEmpty()) {
                logWarningAboutLateFilter();
                synchronized (meterMapLock) {
                    stalePreFilterIds.addAll(preFilterIdToMeterMap.keySet());
                }
            }
            MeterFilter[] newFilters = new MeterFilter[filters.length + 1];
            System.arraycopy(filters, 0, newFilters, 0, filters.length);
            newFilters[filters.length] = filter;
            filters = newFilters;
            return this;
        }

        private void logWarningAboutLateFilter() {
            InternalLogger logger = InternalLoggerFactory.getInstance(MeterRegistry.this.getClass());
            String baseMessage = "A MeterFilter is being configured after a Meter has been registered to this registry. All MeterFilters should be configured before any Meters are registered. If that is not possible or you have a use case where it should be allowed, let the Micrometer maintainers know at https://github.com/micrometer-metrics/micrometer/issues/4920.";
            if (logger.isDebugEnabled()) {
                String stackTrace = Arrays.stream(Thread.currentThread().getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n\tat "));
                logger.warn(baseMessage + "\n" + stackTrace);
            }
            else {
                logger.warn(baseMessage
                        + " Enable DEBUG level logging on this logger to see a stack trace of the call configuring this MeterFilter.");
            }
        }

        /**
         * Register an event listener for each meter added to the registry.
         * @param meterAddedListener a meter-added event listener to be added
         * @return This configuration instance.
         */
        public Config onMeterAdded(Consumer<Meter> meterAddedListener) {
            meterAddedListeners.add(meterAddedListener);
            return this;
        }

        /**
         * Register an event listener for each meter removed from the registry.
         * @param meterRemovedListener a meter-removed event listener to be added
         * @return This configuration instance.
         * @since 1.1.0
         */
        @Incubating(since = "1.1.0")
        public Config onMeterRemoved(Consumer<Meter> meterRemovedListener) {
            meterRemovedListeners.add(meterRemovedListener);
            return this;
        }

        /**
         * Register an event listener for meter registration failures.
         * @param meterRegistrationFailedListener An event listener for meter registration
         * failures
         * @return This configuration instance
         * @since 1.6.0
         */
        @Incubating(since = "1.6.0")
        public Config onMeterRegistrationFailed(BiConsumer<Id, String> meterRegistrationFailedListener) {
            meterRegistrationFailedListeners.add(meterRegistrationFailedListener);
            return this;
        }

        /**
         * Use the provided naming convention, overriding the default for your monitoring
         * system.
         * @param convention The naming convention to use.
         * @return This configuration instance.
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
         * @return The clock used to measure durations of timers and long task timers (and
         * sometimes influences publishing behavior).
         */
        public Clock clock() {
            return clock;
        }

        /**
         * Sets the default pause detector to use for all timers in this registry.
         * @param detector The pause detector to use.
         * @return This configuration instance.
         * @see io.micrometer.core.instrument.distribution.pause.NoPauseDetector
         * @see io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector
         */
        public Config pauseDetector(PauseDetector detector) {
            pauseDetector = detector;
            return this;
        }

        /**
         * @return The pause detector that is currently in effect.
         */
        public PauseDetector pauseDetector() {
            return pauseDetector;
        }

        /**
         * Creates and starts a new {@link HighCardinalityTagsDetector} for this registry.
         * @return This configuration instance.
         * @since 1.10.0
         */
        public Config withHighCardinalityTagsDetector() {
            return withHighCardinalityTagsDetector(new HighCardinalityTagsDetector(MeterRegistry.this));
        }

        /**
         * Creates and starts a new {@link HighCardinalityTagsDetector} for this registry.
         * @param threshold The threshold to use to detect high cardinality tags (if the
         * number of Meters with the same name is higher than this value, that's a high
         * cardinality tag).
         * @param delay The delay between the termination of one check and the
         * commencement of the next.
         * @return This configuration instance.
         * @since 1.10.0
         */
        public Config withHighCardinalityTagsDetector(long threshold, Duration delay) {
            return withHighCardinalityTagsDetector(
                    new HighCardinalityTagsDetector(MeterRegistry.this, threshold, delay));
        }

        /**
         * Uses the supplied {@code Function<MeterRegistry, HighCardinalityTagsDetector>}
         * to create a new {@link HighCardinalityTagsDetector} for this registry. After
         * the {@link HighCardinalityTagsDetector} is created, it also starts it. The
         * implementation of the factory {@code Function} must pass the registry instance
         * to one of the constructors of {@link HighCardinalityTagsDetector}.
         * @param highCardinalityTagsDetectorFactory The {@code Function} that creates the
         * {@link HighCardinalityTagsDetector} instance
         * @return This configuration instance.
         * @since 1.14.0
         */
        public Config withHighCardinalityTagsDetector(
                Function<MeterRegistry, HighCardinalityTagsDetector> highCardinalityTagsDetectorFactory) {
            return withHighCardinalityTagsDetector(highCardinalityTagsDetectorFactory.apply(MeterRegistry.this));
        }

        private Config withHighCardinalityTagsDetector(HighCardinalityTagsDetector newHighCardinalityTagsDetector) {
            if (highCardinalityTagsDetector != null) {
                highCardinalityTagsDetector.close();
            }

            highCardinalityTagsDetector = newHighCardinalityTagsDetector;
            highCardinalityTagsDetector.start();

            return this;
        }

        /**
         * Returns the current {@link HighCardinalityTagsDetector}. You can "deregister"
         * it by calling {@link HighCardinalityTagsDetector#close()} or register a new one
         * by closing the previous one and creating a new one.
         * @return The {@link HighCardinalityTagsDetector} that is currently in effect.
         * @since 1.10.0
         */
        @Nullable
        public HighCardinalityTagsDetector highCardinalityTagsDetector() {
            return highCardinalityTagsDetector;
        }

    }

    /**
     * Additional, less commonly used meter types.
     */
    public class More {

        /**
         * Measures the time taken for long tasks.
         * @param name Name of the long task timer being registered.
         * @param tags MUST be an even number of arguments representing key/value pairs of
         * tags.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, String... tags) {
            return longTaskTimer(name, Tags.of(tags));
        }

        /**
         * Measures the time taken for long tasks.
         * @param name Name of the long task timer being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return LongTaskTimer.builder(name).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Only used by {@link LongTaskTimer#builder(String)}.
         * @param id The identifier for this long task timer.
         * @return A new or existing long task timer.
         */
        LongTaskTimer longTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
            return registerMeterIfNecessary(LongTaskTimer.class, id, distributionStatisticConfig,
                    (id2, filteredConfig) -> {
                        Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
                        return newLongTaskTimer(withUnit, filteredConfig.merge(defaultHistogramConfig()));
                    }, NoopLongTaskTimer::new);
        }

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter
         * whenever the value is observed.
         * @param name Name of the counter being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @param obj State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter
         * value from the state object.
         * @param <T> The type of the state object from which the counter value is
         * extracted.
         * @return A new or existing function counter.
         */
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> countFunction) {
            return FunctionCounter.builder(name, obj, countFunction).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         * @param name Name of the counter being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @param number A monotonically increasing number to track.
         * @param <T> The type of the state object from which the counter value is
         * extracted.
         * @return A new or existing function counter.
         */
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return FunctionCounter.builder(name, number, Number::doubleValue).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         * @param id The identifier for this function counter.
         * @param obj State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter
         * value from the state object.
         * @param <T> The type of the state object from which the counter value is
         * extracted.
         * @return A new or existing function counter.
         */
        <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
            return registerMeterIfNecessary(FunctionCounter.class, id,
                    id2 -> newFunctionCounter(id2, obj, countFunction), NoopFunctionCounter::new);
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         * @param name Name of the timer being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @param obj State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter
         * value from the state object.
         * @param totalTimeFunction Function that produces a monotonically increasing
         * total time value from the state object.
         * @param totalTimeFunctionUnit The base unit of time produced by the total time
         * function.
         * @param <T> The type of the state object from which the function values are
         * extracted.
         * @return A new or existing function timer.
         */
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj, ToLongFunction<T> countFunction,
                ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
            return FunctionTimer.builder(name, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit)
                .tags(tags)
                .register(MeterRegistry.this);
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         * @param id The identifier for this function timer.
         * @param obj State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter
         * value from the state object.
         * @param totalTimeFunction Function that produces a monotonically increasing
         * total time value from the state object.
         * @param totalTimeFunctionUnit The base unit of time produced by the total time
         * function.
         * @param <T> The type of the state object from which the function values are
         * extracted.F
         * @return A new or existing function timer.
         */
        <T> FunctionTimer timer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
                ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
            return registerMeterIfNecessary(FunctionTimer.class, id, id2 -> {
                Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
                return newFunctionTimer(withUnit, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit);
            }, NoopFunctionTimer::new);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base
         * time unit.
         * @param name Name of the gauge being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @param obj State object used to compute a value.
         * @param timeFunctionUnit The base unit of time produced by the total time
         * function.
         * @param timeFunction Function that produces a time value from the state object.
         * This value may increase and decrease over time.
         * @param <T> The type of the state object from which the gauge value is
         * extracted.
         * @return A new or existing time gauge.
         */
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj, TimeUnit timeFunctionUnit,
                ToDoubleFunction<T> timeFunction) {
            return TimeGauge.builder(name, obj, timeFunctionUnit, timeFunction).tags(tags).register(MeterRegistry.this);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base
         * time unit.
         * @param id The identifier for this time gauge.
         * @param obj State object used to compute a value.
         * @param timeFunctionUnit The base unit of time produced by the total time
         * function.
         * @param timeFunction Function that produces a time value from the state object.
         * This value may increase and decrease over time.
         * @param <T> The type of the state object from which the gauge value is
         * extracted.
         * @return A new or existing time gauge.
         */
        <T> TimeGauge timeGauge(Meter.Id id, @Nullable T obj, TimeUnit timeFunctionUnit,
                ToDoubleFunction<T> timeFunction) {
            return registerMeterIfNecessary(TimeGauge.class, id,
                    id2 -> newTimeGauge(id2, obj, timeFunctionUnit, timeFunction), NoopTimeGauge::new);
        }

    }

    /**
     * Closes this registry, releasing any resources in the process. Once closed, this
     * registry will no longer accept new meters and any publishing activity will cease.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (meterMapLock) {
                for (Meter meter : meterMap.values()) {
                    meter.close();
                }
            }
        }

        if (highCardinalityTagsDetector != null) {
            highCardinalityTagsDetector.close();
        }
    }

    /**
     * If the registry is closed, it will no longer accept new meters and any publishing
     * activity will cease.
     * @return {@code true} if this registry is closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Handle a meter registration failure.
     * @param id The id that was attempted, but for which registration failed.
     * @param reason The reason why the meter registration has failed
     * @since 1.6.0
     */
    protected void meterRegistrationFailed(Meter.Id id, @Nullable String reason) {
        for (BiConsumer<Id, String> listener : meterRegistrationFailedListeners) {
            listener.accept(id, reason);
        }
    }

    // VisibleForTesting
    Map<Id, Meter> _getPreFilterIdToMeterMap() {
        return Collections.unmodifiableMap(preFilterIdToMeterMap);
    }

    // VisibleForTesting
    Map<Meter, Id> _getMeterToPreFilterIdMap() {
        return Collections.unmodifiableMap(meterToPreFilterIdMap);
    }

    // VisibleForTesting
    Set<Id> _getStalePreFilterIds() {
        return Collections.unmodifiableSet(stalePreFilterIds);
    }

}
