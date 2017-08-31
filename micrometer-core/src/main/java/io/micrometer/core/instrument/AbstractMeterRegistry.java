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
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    protected final Clock clock;

    /**
     * List of common tags to append to every metric, stored pre-formatted.
     */
    private final List<Tag> commonTags = new ArrayList<>();

    private final ConcurrentMap<MeterId, Meter> meterMap = new ConcurrentHashMap<>();

    /**
     * We'll use snake case as a general-purpose default for registries because it is the most
     * likely to result in a portable name. Camel casing is also perfectly acceptable. '-' and '.'
     * separators can pose problems for some monitoring systems. '-' is interpreted as metric
     * subtraction in some (including Prometheus), and '.' is used to flatten tags into hierarchical
     * names when shipping metrics to hierarchical backends such as Graphite.
     */
    private NamingConvention namingConvention = NamingConvention.snakeCase;

    private TimeUnit baseTimeUnit = TimeUnit.NANOSECONDS;

    private MeterRegistry.Config config = new MeterRegistry.Config() {
        @Override
        public Config commonTags(Iterable<Tag> tags) {
            stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .forEach(commonTags::add);
            return this;
        }

        @Override
        public Iterable<Tag> commonTags() {
            return commonTags;
        }

        @Override
        public Config namingConvention(NamingConvention convention) {
            namingConvention = convention;
            return this;
        }

        @Override
        public NamingConvention namingConvention() {
            return namingConvention;
        }

        @Override
        public Clock clock() {
            return clock;
        }
    };

    @Override
    public MeterRegistry.Config config() {
        return config;
    }

    public AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    protected abstract <T> Gauge newGauge(Meter.Id id, String description, ToDoubleFunction<T> f, T obj);
    protected abstract Counter newCounter(Meter.Id id, String description);
    protected abstract LongTaskTimer newLongTaskTimer(Meter.Id id, String description);
    protected abstract Timer newTimer(Meter.Id id, String description, Histogram.Builder<?> histogram, Quantiles quantiles);
    protected abstract DistributionSummary newDistributionSummary(Meter.Id id, String description, Histogram.Builder<?> histogram, Quantiles quantiles);
    protected abstract void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements);

    @Override
    public Meter register(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        return meterMap.computeIfAbsent(new MeterId(name, tags, type, null), id -> {
            newMeter(id, type, measurements);
            return new Meter() {
                @Override
                public Id getId() {
                    return id;
                }

                @Override
                public String getDescription() {
                    return null;
                }

                @Override
                public Iterable<Measurement> measure() {
                    return measurements;
                }
            };
        });
    }

    @Override
    public final <T> Gauge.Builder gaugeBuilder(String name, T obj, ToDoubleFunction<T> f) {
        return new GaugeBuilder<>(name, obj, f);
    }

    private class GaugeBuilder<T> implements Gauge.Builder {
        private final String name;
        private final T obj;
        private final ToDoubleFunction<T> f;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private GaugeBuilder(String name, T obj, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.f = f;
        }

        @Override
        public Gauge.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public Gauge.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Gauge.Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        @Override
        public Gauge create() {
            return registerMeterIfNecessary(Gauge.class, Meter.Type.Gauge, name, tags, baseUnit, id ->
                newGauge(id, description, f, obj));
        }
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return new TimerBuilder(name);
    }

    private class TimerBuilder implements Timer.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram.Builder<?> histogram;
        private final List<Tag> tags = new ArrayList<>();
        private String description;

        private TimerBuilder(String name) {
            this.name = name;
        }

        @Override
        public Timer.Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public Timer.Builder histogram(Histogram.Builder<?> histogram) {
            this.histogram = histogram;
            return this;
        }

        @Override
        public Timer.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Timer.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Timer create() {
            // the base unit for a timer will be determined by the monitoring system if it is part of the convention name
            return registerMeterIfNecessary(Timer.class, Meter.Type.Timer, name, tags, baseTimeUnit.toString().toLowerCase(), id ->
                newTimer(id, description, histogram, quantiles));
        }
    }

    @Override
    public DistributionSummary.Builder summaryBuilder(String name) {
        return new DistributionSummaryBuilder(name);
    }

    private class DistributionSummaryBuilder implements DistributionSummary.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram.Builder<?> histogram;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private DistributionSummaryBuilder(String name) {
            this.name = name;
        }

        @Override
        public DistributionSummary.Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public DistributionSummary.Builder histogram(Histogram.Builder<?> histogram) {
            this.histogram = histogram;
            return this;
        }

        @Override
        public DistributionSummary.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public DistributionSummary.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DistributionSummary.Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        @Override
        public DistributionSummary create() {
            return registerMeterIfNecessary(DistributionSummary.class, Meter.Type.DistributionSummary, name, tags, baseUnit, id ->
                newDistributionSummary(id, description, histogram, quantiles));
        }
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return new CounterBuilder(name);
    }

    private class CounterBuilder implements Counter.Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private CounterBuilder(String name) {
            this.name = name;
        }

        @Override
        public Counter.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Counter.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Counter.Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        @Override
        public Counter create() {
            return registerMeterIfNecessary(Counter.class, Meter.Type.Counter, name, tags, baseUnit, id ->
                newCounter(id, description));
        }
    }

    private MeterRegistry.More more = new MeterRegistry.More() {
        @Override
        public LongTaskTimer.Builder longTaskTimerBuilder(String name) {
            return new LongTaskTimerBuilder(name);
        }

        @Override
        public <T> Meter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
            WeakReference<T> ref = new WeakReference<>(obj);
            return register(name, tags, Meter.Type.Counter,
                Collections.singletonList(new Measurement(() -> {
                    T obj2 = ref.get();
                    return obj2 != null ? f.applyAsDouble(obj2) : 0;
                }, Statistic.Count)));
        }
    };

    private class LongTaskTimerBuilder implements LongTaskTimer.Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        private String description;

        private LongTaskTimerBuilder(String name) {
            this.name = name;
        }

        @Override
        public LongTaskTimer.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public LongTaskTimer.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public LongTaskTimer create() {
            return registerMeterIfNecessary(LongTaskTimer.class, Meter.Type.LongTaskTimer, name, tags, null, id ->
                newLongTaskTimer(id, description));
        }
    }

    @Override
    public More more() {
        return more;
    }

    private class SearchImpl implements Search {
        private final String name;
        private List<Tag> tags = new ArrayList<>();
        private Map<Statistic, Double> valueAsserts = new HashMap<>();

        SearchImpl(String name) {
            this.name = name;
        }

        @Override
        public Search tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public Search value(Statistic statistic, double value) {
            valueAsserts.put(statistic, value);
            return this;
        }

        @Override
        public Optional<Timer> timer() {
            return meters()
                .stream()
                .filter(m -> m instanceof Timer)
                .findAny()
                .map(Timer.class::cast);
        }

        @Override
        public Optional<Counter> counter() {
            return meters()
                .stream()
                .filter(m -> m instanceof Counter)
                .findAny()
                .map(Counter.class::cast);
        }

        @Override
        public Optional<Gauge> gauge() {
            return meters()
                .stream()
                .filter(m -> m instanceof Gauge)
                .findAny()
                .map(Gauge.class::cast);
        }

        @Override
        public Optional<DistributionSummary> summary() {
            return meters()
                .stream()
                .filter(m -> m instanceof DistributionSummary)
                .findAny()
                .map(DistributionSummary.class::cast);
        }

        @Override
        public Optional<LongTaskTimer> longTaskTimer() {
            return meters()
                .stream()
                .filter(m -> m instanceof LongTaskTimer)
                .findAny()
                .map(LongTaskTimer.class::cast);
        }

        @Override
        public Optional<Meter> meter() {
            return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> {
                    List<Tag> idTags = new ArrayList<>();
                    id.getTags().forEach(idTags::add);
                    return idTags.containsAll(tags);
                })
                .map(meterMap::get)
                .filter(m -> {
                    for (Measurement measurement : m.measure()) {
                        if(valueAsserts.getOrDefault(measurement.getStatistic(), measurement.getValue()) != measurement.getValue()) {
                            return false;
                        }
                    }
                    return true;
                })
                .findAny();
        }

        @Override
        public Collection<Meter> meters() {
            return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> {
                    List<Tag> idTags = new ArrayList<>();
                    id.getTags().forEach(idTags::add);
                    return idTags.containsAll(tags);
                })
                .map(meterMap::get)
                .collect(Collectors.toList());
        }
    }

    @Override
    public Search find(String name) {
        return new SearchImpl(name);
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    class MeterId implements Meter.Id {
        private final String name;
        private final List<Tag> tags;
        private final Meter.Type type;
        private final String baseUnit;

        MeterId(String name, Iterable<Tag> tags, Meter.Type type, String baseUnit) {
            this.name = name;
            this.tags = Stream.concat(stream(tags.spliterator(), false), commonTags.stream()).collect(Collectors.toList());
            this.type = type;
            this.baseUnit = baseUnit;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Iterable<Tag> getTags() {
            return tags;
        }

        @Override
        public String getConventionName() {
            return namingConvention.name(name, type, baseUnit);
        }

        /**
         * Tags that are sorted by key and formatted
         */
        @Override
        public List<Tag> getConventionTags() {
            return tags.stream()
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .sorted(Comparator.comparing(Tag::getKey))
                .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "MeterId{" +
                "name='" + name + '\'' +
                ", tags=" + tags +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MeterId meterId = (MeterId) o;
            return (name != null ? name.equals(meterId.name) : meterId.name == null) && (tags != null ? tags.equals(meterId.tags) : meterId.tags == null);
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (tags != null ? tags.hashCode() : 0);
            return result;
        }
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Type type, String name, Iterable<Tag> tags, String baseUnit, Function<MeterId, Meter> builder) {
        if(name.isEmpty()) {
            throw new IllegalArgumentException("Name must be non-empty");
        }

        synchronized (meterMap) {
            Meter m = meterMap.computeIfAbsent(new MeterId(name, tags, type, baseUnit), builder);
            if (!meterClass.isInstance(m)) {
                throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
            }
            //noinspection unchecked
            return (M) m;
        }
    }
}
