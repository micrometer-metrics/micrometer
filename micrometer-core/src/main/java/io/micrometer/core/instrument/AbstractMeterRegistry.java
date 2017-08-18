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

    private MeterRegistry.Config config = new MeterRegistry.Config() {
        @Override
        public Config commonTags(Iterable<Tag> tags) {
            stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .forEach(commonTags::add);
            return this;
        }

        @Override
        public Config namingConvention(NamingConvention convention) {
            namingConvention = convention;
            return this;
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

    protected abstract DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, String description, Quantiles quantiles, Histogram<?> histogram);
    protected abstract <T> Gauge newGauge(String name, Iterable<Tag> tags, String description, ToDoubleFunction<T> f, T obj);
    protected abstract Counter newCounter(String name, Iterable<Tag> tags, String description);
    protected abstract LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags, String description);
    protected abstract Timer newTimer(String name, Iterable<Tag> tags, String description, Histogram<?> histogram, Quantiles quantiles);
    protected abstract void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements);

    @Override
    public MeterRegistry register(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        meterMap.computeIfAbsent(new MeterId(name, tags), id -> {
            newMeter(id.getConventionName(type), id.getTags(), type, measurements);
            return new Meter() {
                @Override
                public String getName() {
                    return id.getName();
                }

                @Override
                public Iterable<Tag> getTags() {
                    return id.getTags();
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
        return this;
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

        public Gauge.Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Gauge create() {
            return registerMeterIfNecessary(Gauge.class, name, tags, id ->
                newGauge(id.getConventionName(Meter.Type.Gauge), id.getTags(), description, f, obj));
        }
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return new TimerBuilder(name);
    }

    private class TimerBuilder implements Timer.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram<?> histogram;
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

        public Timer.Builder histogram(Histogram histogram) {
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
            return registerMeterIfNecessary(Timer.class, name, tags, id ->
                newTimer(id.getConventionName(Meter.Type.Timer), id.getTags(), description, histogram, quantiles));
        }
    }

    @Override
    public DistributionSummary.Builder summaryBuilder(String name) {
        return new DistributionSummaryBuilder(name);
    }

    private class DistributionSummaryBuilder implements DistributionSummary.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram<?> histogram;
        private final List<Tag> tags = new ArrayList<>();
        private String description;

        private DistributionSummaryBuilder(String name) {
            this.name = name;
        }

        @Override
        public DistributionSummary.Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public DistributionSummary.Builder histogram(Histogram<?> histogram) {
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
        public DistributionSummary create() {
            return registerMeterIfNecessary(DistributionSummary.class, name, tags, id ->
                newDistributionSummary(id.getConventionName(Meter.Type.DistributionSummary), id.getTags(), description, quantiles, histogram));
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
        public Counter create() {
            return registerMeterIfNecessary(Counter.class, name, tags, id ->
                newCounter(id.getConventionName(Meter.Type.Counter), id.getTags(), description));
        }
    }

    private MeterRegistry.More more = new MeterRegistry.More() {
        @Override
        public LongTaskTimer.Builder longTaskTimerBuilder(String name) {
            return new LongTaskTimerBuilder(name);
        }

        @Override
        public <T> T counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
            WeakReference<T> ref = new WeakReference<>(obj);
            register(name, tags, Meter.Type.Counter,
                Collections.singletonList(new Measurement(() -> {
                    T obj2 = ref.get();
                    return obj2 != null ? f.applyAsDouble(obj2) : 0;
                }, Statistic.Count)));
            return obj;
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
            return registerMeterIfNecessary(LongTaskTimer.class, name, tags, id ->
                newLongTaskTimer(id.getConventionName(Meter.Type.LongTaskTimer), id.getTags(), description));
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
            MeterId id = new MeterId(name, tags);

            return meterMap.keySet().stream()
                .filter(id2 -> id2.getName().equals(id.getName()))
                .filter(id2 -> id2.getTags().containsAll(id.getTags()))
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
            MeterId id = new MeterId(name, tags);

            return meterMap.keySet().stream()
                .filter(id2 -> id2.getName().equals(id.getName()))
                .filter(id2 -> id2.getTags().containsAll(id.getTags()))
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

    /**
     * Used to hold a unique identifier for Meters (a combination of their name and tags). Should
     * never be exposed to users through a public API.
     */
    class MeterId {
        private final String name;
        private final Iterable<Tag> tags;

        MeterId(String name, Iterable<Tag> tags) {
            this.name = name;
            this.tags = tags;
        }

        String getName() {
            return name;
        }

        /**
         * The formatted name matching this registry's naming convention
         */
        String getConventionName(Meter.Type type) {
            return namingConvention.name(name, type);
        }

        /**
         * Tags that are sorted by key and formatted
         */
        public List<Tag> getTags() {
            Stream<Tag> formattedTags = stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())));

            return Stream.concat(formattedTags, commonTags.stream())
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

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterType, String name, Iterable<Tag> tags, Function<MeterId, Meter> builder) {
        if(name.isEmpty()) {
            throw new IllegalArgumentException("Name must be non-empty");
        }

        synchronized (meterMap) {
            Meter m = meterMap.computeIfAbsent(new MeterId(name, tags), builder);
            if (!meterType.isInstance(m)) {
                throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
            }
            //noinspection unchecked
            return (M) m;
        }
    }
}
