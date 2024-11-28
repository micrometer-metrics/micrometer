/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.search.Search;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static io.micrometer.health.QueryUtils.MAX_OR_NAN;
import static io.micrometer.health.QueryUtils.SUM_OR_NAN;

/**
 * Service level objective.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.6.0
 */
public abstract class ServiceLevelObjective {

    private static final ThreadLocal<DecimalFormat> WHOLE_OR_SHORT_DECIMAL = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.US);
        return new DecimalFormat("##0.##", otherSymbols);
    });

    private final String name;

    private final Tags tags;

    @Nullable
    private final String baseUnit;

    /**
     * Describes what it means for this service level objective to not be met.
     */
    @Nullable
    private final String failedMessage;

    private final Meter.Id id;

    protected ServiceLevelObjective(String name, Tags tags, @Nullable String baseUnit, @Nullable String failedMessage) {
        this.name = name;
        this.tags = tags;
        this.baseUnit = baseUnit;
        this.failedMessage = failedMessage;
        this.id = new Meter.Id(name, tags, baseUnit, failedMessage, Meter.Type.GAUGE);
    }

    public String getName() {
        return name;
    }

    public Iterable<Tag> getTags() {
        return tags;
    }

    @Nullable
    public String getBaseUnit() {
        return baseUnit;
    }

    public Meter.Id getId() {
        return id;
    }

    @Nullable
    public String getFailedMessage() {
        return failedMessage;
    }

    public abstract Collection<MeterBinder> getRequires();

    public abstract Collection<MeterFilter> getAcceptFilters();

    public abstract void tick(MeterRegistry registry);

    public abstract boolean healthy(MeterRegistry registry);

    public static MultipleIndicator.Builder compose(String name, ServiceLevelObjective... objectives) {
        return new MultipleIndicator.Builder(name, objectives);
    }

    public static SingleIndicator.Builder build(String name) {
        return new SingleIndicator.Builder(name);
    }

    public static class SingleIndicator extends ServiceLevelObjective {

        private final NumericQuery query;

        private final Collection<MeterBinder> requires;

        private final String testDescription;

        private final Predicate<Double> test;

        protected SingleIndicator(NumericQuery query, String testDescription, Predicate<Double> test) {
            super(query.name, query.tags, query.baseUnit, query.failedMessage);
            this.query = query;
            this.requires = query.requires;
            this.testDescription = testDescription;
            this.test = test;
        }

        @Override
        public boolean healthy(MeterRegistry registry) {
            Double v = getValue(registry);
            return v.isNaN() || test.test(v);
        }

        @Override
        public void tick(MeterRegistry registry) {
            query.tick(registry);
        }

        @Override
        public Collection<MeterBinder> getRequires() {
            return requires;
        }

        @Override
        public Collection<MeterFilter> getAcceptFilters() {
            return query.acceptFilters();
        }

        public double getValue(MeterRegistry registry) {
            return query.getValue(registry);
        }

        public String getValueAsString(MeterRegistry registry) {
            double value = getValue(registry);
            return Double.isNaN(value) ? "no value available"
                    : getBaseUnit() != null && getBaseUnit().toLowerCase(Locale.ROOT).contains("percent")
                            ? WHOLE_OR_SHORT_DECIMAL.get().format(value * 100) + "%"
                            : WHOLE_OR_SHORT_DECIMAL.get().format(value);
        }

        public String getTestDescription() {
            return testDescription;
        }

        static SingleIndicator testNumeric(NumericQuery query, String testDescription, Predicate<Double> test) {
            return new SingleIndicator(query, testDescription, test);
        }

        static SingleIndicator testDuration(NumericQuery query, String testDescription, Predicate<Duration> test) {
            return new SingleIndicator(query, testDescription,
                    valueNanos -> valueNanos.isNaN() || test.test(Duration.ofNanos(valueNanos.longValue())));
        }

        public static class Builder {

            private final String name;

            private Tags tags = Tags.empty();

            @Nullable
            private String baseUnit;

            @Nullable
            private String failedMessage;

            private final Collection<MeterBinder> requires;

            Builder(String name) {
                this(name, null, new ArrayList<>());
            }

            Builder(String name, @Nullable String failedMessage, Collection<MeterBinder> requires) {
                this.name = name;
                this.failedMessage = failedMessage;
                this.requires = requires;
            }

            public final Builder failedMessage(@Nullable String failedMessage) {
                this.failedMessage = failedMessage;
                return this;
            }

            public final Builder requires(MeterBinder... requires) {
                Collections.addAll(this.requires, requires);
                return this;
            }

            public final Builder baseUnit(@Nullable String baseUnit) {
                this.baseUnit = baseUnit;
                return this;
            }

            /**
             * @param tags Must be an even number of arguments representing key/value
             * pairs of tags.
             * @return This builder.
             */
            public final Builder tags(String... tags) {
                return tags(Tags.of(tags));
            }

            /**
             * @param tags Tags to add to the single indicator.
             * @return The builder with added tags.
             */
            public final Builder tags(Iterable<Tag> tags) {
                this.tags = this.tags.and(tags);
                return this;
            }

            /**
             * @param key The tag key.
             * @param value The tag value.
             * @return The single indicator builder with a single added tag.
             */
            public final Builder tag(String key, String value) {
                this.tags = tags.and(key, value);
                return this;
            }

            public final NumericQuery count(Function<Search, Search> search) {
                return new Instant(name, tags, baseUnit, failedMessage, requires, search,
                        s -> s.meters().stream().map(m -> {
                            if (m instanceof Counter) {
                                return ((Counter) m).count();
                            }
                            else if (m instanceof Timer) {
                                return (double) ((Timer) m).count();
                            }
                            else if (m instanceof FunctionTimer) {
                                return ((FunctionTimer) m).count();
                            }
                            else if (m instanceof FunctionCounter) {
                                return ((FunctionCounter) m).count();
                            }
                            else if (m instanceof LongTaskTimer) {
                                return (double) ((LongTaskTimer) m).activeTasks();
                            }
                            return Double.NaN;
                        }).reduce(Double.NaN, SUM_OR_NAN));
            }

            public NumericQuery errorRatio(Function<Search, Search> searchAll, Function<Search, Search> searchErrors) {
                return count(searchAll.andThen(searchErrors)).dividedBy(over -> over.count(searchAll));
            }

            public final NumericQuery total(Function<Search, Search> search) {
                return new Instant(name, tags, baseUnit, failedMessage, requires, search,
                        s -> s.meters().stream().map(m -> {
                            if (m instanceof DistributionSummary) {
                                return ((DistributionSummary) m).totalAmount();
                            }
                            else if (m instanceof Timer) {
                                return ((Timer) m).totalTime(TimeUnit.NANOSECONDS);
                            }
                            else if (m instanceof LongTaskTimer) {
                                return ((LongTaskTimer) m).duration(TimeUnit.NANOSECONDS);
                            }
                            return Double.NaN;
                        }).reduce(Double.NaN, SUM_OR_NAN));
            }

            public final NumericQuery maxPercentile(Function<Search, Search> search, double percentile) {
                return new Instant(name, tags, baseUnit, failedMessage, requires, search,
                        s -> s.meters().stream().map(m -> {
                            if (!(m instanceof HistogramSupport)) {
                                return Double.NaN;
                            }

                            ValueAtPercentile[] valueAtPercentiles = ((HistogramSupport) m).takeSnapshot()
                                .percentileValues();
                            return Arrays.stream(valueAtPercentiles)
                                .filter(vap -> vap.percentile() == percentile)
                                .map(ValueAtPercentile::value)
                                .findAny()
                                .orElse(Double.NaN);
                        }).reduce(Double.NaN, MAX_OR_NAN));
            }

            public final NumericQuery max(Function<Search, Search> search) {
                return new Instant(name, tags, baseUnit, failedMessage, requires, search,
                        s -> s.meters().stream().map(m -> {
                            if (m instanceof DistributionSummary) {
                                return ((DistributionSummary) m).max();
                            }
                            else if (m instanceof Timer) {
                                return ((Timer) m).max(TimeUnit.NANOSECONDS);
                            }
                            else if (m instanceof LongTaskTimer) {
                                return ((LongTaskTimer) m).max(TimeUnit.NANOSECONDS);
                            }
                            return Double.NaN;
                        }).reduce(Double.NaN, MAX_OR_NAN));
            }

            /**
             * @param search The search criteria for a {@link Gauge}.
             * @return The value of the first matching gauge time series.
             */
            public final NumericQuery value(Function<Search, Search> search) {
                return new Instant(name, tags, baseUnit, failedMessage, requires, search,
                        s -> s.meters().stream().map(m -> {
                            if (m instanceof TimeGauge) {
                                return ((TimeGauge) m).value(TimeUnit.NANOSECONDS);
                            }
                            else if (m instanceof Gauge) {
                                return ((Gauge) m).value();
                            }
                            return Double.NaN;
                        }).filter(n -> !Double.isNaN(n)).findAny().orElse(Double.NaN));
            }

        }

        public abstract static class NumericQuery {

            protected final String name;

            private final Tags tags;

            @Nullable
            private final String baseUnit;

            @Nullable
            private final String failedMessage;

            private final Collection<MeterBinder> requires;

            NumericQuery(String name, Tags tags, @Nullable String baseUnit, @Nullable String failedMessage,
                    Collection<MeterBinder> requires) {
                this.name = name;
                this.tags = tags;
                this.baseUnit = baseUnit;
                this.failedMessage = failedMessage;
                this.requires = requires;
            }

            abstract Double getValue(MeterRegistry registry);

            private String thresholdString(double threshold) {
                return baseUnit != null && baseUnit.toLowerCase(Locale.ROOT).contains("percent")
                        ? WHOLE_OR_SHORT_DECIMAL.get().format(threshold * 100) + "%"
                        : WHOLE_OR_SHORT_DECIMAL.get().format(threshold);
            }

            public final SingleIndicator isLessThan(double threshold) {
                return SingleIndicator.testNumeric(this, "<" + thresholdString(threshold), v -> v < threshold);
            }

            public final SingleIndicator isLessThanOrEqualTo(double threshold) {
                return SingleIndicator.testNumeric(this, "<=" + thresholdString(threshold), v -> v <= threshold);
            }

            public final SingleIndicator isGreaterThan(double threshold) {
                return SingleIndicator.testNumeric(this, ">" + thresholdString(threshold), v -> v > threshold);
            }

            public final SingleIndicator isGreaterThanOrEqualTo(double threshold) {
                return SingleIndicator.testNumeric(this, ">=" + thresholdString(threshold), v -> v >= threshold);
            }

            public final SingleIndicator isEqualTo(double threshold) {
                return SingleIndicator.testNumeric(this, "==" + thresholdString(threshold), v -> v == threshold);
            }

            public final SingleIndicator isLessThan(Duration threshold) {
                return SingleIndicator.testDuration(this, "<" + threshold, v -> v.compareTo(threshold) < 0);
            }

            public final SingleIndicator isLessThanOrEqualTo(Duration threshold) {
                return SingleIndicator.testDuration(this, "<=" + threshold, v -> v.compareTo(threshold) <= 0);
            }

            public final SingleIndicator isGreaterThan(Duration threshold) {
                return SingleIndicator.testDuration(this, ">" + threshold, v -> v.compareTo(threshold) > 0);
            }

            public final SingleIndicator isGreaterThanOrEqualTo(Duration threshold) {
                return SingleIndicator.testDuration(this, ">=" + threshold, v -> v.compareTo(threshold) >= 0);
            }

            public final SingleIndicator isEqualTo(Duration threshold) {
                return SingleIndicator.testDuration(this, "==" + threshold, v -> v.compareTo(threshold) == 0);
            }

            public final SingleIndicator test(String thresholdDescription, Predicate<Double> threshold) {
                return SingleIndicator.testNumeric(this, thresholdDescription, threshold);
            }

            public final SingleIndicator testDuration(String thresholdDescription, Predicate<Duration> threshold) {
                return SingleIndicator.testDuration(this, thresholdDescription, threshold);
            }

            public final NumericQuery dividedBy(Function<SingleIndicator.Builder, NumericQuery> over) {
                return new ArithmeticOp(this, over.apply(new Builder(name, failedMessage, requires)),
                        (v1, v2) -> v1 / v2);
            }

            public final NumericQuery multipliedBy(Function<SingleIndicator.Builder, NumericQuery> by) {
                return new ArithmeticOp(this, by.apply(new Builder(name, failedMessage, requires)),
                        (v1, v2) -> v1 * v2);
            }

            public final NumericQuery plus(Function<SingleIndicator.Builder, NumericQuery> with) {
                return new ArithmeticOp(this, with.apply(new Builder(name, failedMessage, requires)), Double::sum);
            }

            public final NumericQuery minus(Function<SingleIndicator.Builder, NumericQuery> with) {
                return new ArithmeticOp(this, with.apply(new Builder(name, failedMessage, requires)),
                        (v1, v2) -> v1 - v2);
            }

            public final NumericQuery combineWith(Function<SingleIndicator.Builder, NumericQuery> with,
                    BinaryOperator<Double> combiner) {
                return new ArithmeticOp(this, with.apply(new Builder(name, failedMessage, requires)), combiner);
            }

            public final NumericQuery maxOver(Duration interval) {
                return new OverInterval(this, interval, vs -> vs.max().orElse(Double.NaN));
            }

            public final NumericQuery minOver(Duration interval) {
                return new OverInterval(this, interval, vs -> vs.min().orElse(Double.NaN));
            }

            public final NumericQuery sumOver(Duration interval) {
                return new OverInterval(this, interval, DoubleStream::sum);
            }

            public final NumericQuery averageOver(Duration interval) {
                return new OverInterval(this, interval, vs -> vs.average().orElse(Double.NaN));
            }

            abstract Collection<MeterFilter> acceptFilters();

            abstract void tick(MeterRegistry registry);

        }

        static class Instant extends NumericQuery {

            private static final CompositeMeterRegistry NOOP_REGISTRY = new CompositeMeterRegistry(Clock.SYSTEM);

            private final Function<Search, Search> search;

            private final Function<Search, Double> toValue;

            Instant(String name, Tags tags, @Nullable String baseUnit, @Nullable String failedMessage,
                    Collection<MeterBinder> requires, Function<Search, Search> search,
                    Function<Search, Double> toValue) {
                super(name, tags, baseUnit, failedMessage, requires);
                this.search = search;
                this.toValue = toValue;
            }

            protected Double getValue(MeterRegistry registry) {
                return toValue.apply(search.apply(Search.in(registry)));
            }

            @Override
            public Collection<MeterFilter> acceptFilters() {
                return Collections.singleton(search.apply(Search.in(NOOP_REGISTRY)).acceptFilter());
            }

            @Override
            public void tick(MeterRegistry registry) {
                // do nothing because the value is always determined from the current
                // instant
            }

        }

        static class ArithmeticOp extends NumericQuery {

            private final NumericQuery left;

            private final NumericQuery right;

            private BinaryOperator<Double> combiner;

            ArithmeticOp(NumericQuery left, NumericQuery right, BinaryOperator<Double> combiner) {
                super(left.name, left.tags, left.baseUnit, left.failedMessage, left.requires);
                this.left = left;
                this.right = right;
                this.combiner = combiner;
            }

            @Override
            protected Double getValue(MeterRegistry registry) {
                return combiner.apply(left.getValue(registry), right.getValue(registry));
            }

            @Override
            public Collection<MeterFilter> acceptFilters() {
                List<MeterFilter> filters = new ArrayList<>();
                filters.addAll(left.acceptFilters());
                filters.addAll(right.acceptFilters());
                return filters;
            }

            @Override
            public void tick(MeterRegistry registry) {
                left.tick(registry);
                right.tick(registry);
            }

        }

        static class OverInterval extends NumericQuery {

            private final Deque<Sample> samples = new ConcurrentLinkedDeque<>();

            private final NumericQuery numericQuery;

            private final Duration interval;

            private final Function<DoubleStream, Double> collector;

            OverInterval(NumericQuery q, Duration interval, Function<DoubleStream, Double> collector) {
                super(q.name, q.tags, q.baseUnit, q.failedMessage, q.requires);
                this.numericQuery = q;
                this.interval = interval;
                this.collector = collector;
            }

            private static class Sample {

                private final long tick;

                private final double sample;

                private Sample(long tick, double sample) {
                    this.tick = tick;
                    this.sample = sample;
                }

            }

            @Override
            protected Double getValue(MeterRegistry registry) {
                return collector.apply(samples.stream().mapToDouble(s -> s.sample).filter(n -> !Double.isNaN(n)));
            }

            @Override
            public Collection<MeterFilter> acceptFilters() {
                return numericQuery.acceptFilters();
            }

            @Override
            public void tick(MeterRegistry registry) {
                long time = registry.config().clock().monotonicTime();

                Sample first = samples.peekFirst();
                if (first != null && Duration.ofNanos(time - first.tick).compareTo(interval) > 0) {
                    samples.removeFirst();
                }

                samples.addLast(new Sample(time, numericQuery.getValue(registry)));
            }

        }

    }

    public static class MultipleIndicator extends ServiceLevelObjective {

        private final ServiceLevelObjective[] objectives;

        private final BinaryOperator<Boolean> combiner;

        MultipleIndicator(String name, Tags tags, @Nullable String failedMessage, ServiceLevelObjective[] objectives,
                BinaryOperator<Boolean> combiner) {
            super(name, tags, null, failedMessage);
            this.objectives = objectives;
            this.combiner = combiner;
        }

        @Override
        public boolean healthy(MeterRegistry registry) {
            return Arrays.stream(objectives).map(o -> o.healthy(registry)).reduce(combiner).orElse(true);
        }

        @Override
        public Collection<MeterBinder> getRequires() {
            return Arrays.stream(objectives).flatMap(o -> o.getRequires().stream()).collect(Collectors.toList());
        }

        public ServiceLevelObjective[] getObjectives() {
            return objectives;
        }

        @Override
        public Collection<MeterFilter> getAcceptFilters() {
            return Arrays.stream(objectives).flatMap(o -> o.getAcceptFilters().stream()).collect(Collectors.toList());
        }

        @Override
        public void tick(MeterRegistry registry) {
            for (ServiceLevelObjective objective : objectives) {
                objective.tick(registry);
            }
        }

        public static class Builder {

            private final String name;

            private Tags tags = Tags.empty();

            private final ServiceLevelObjective[] objectives;

            @Nullable
            private String failedMessage;

            Builder(String name, ServiceLevelObjective[] objectives) {
                this.name = name;
                this.objectives = objectives;
            }

            public final Builder failedMessage(@Nullable String failedMessage) {
                this.failedMessage = failedMessage;
                return this;
            }

            /**
             * @param tags Must be an even number of arguments representing key/value
             * pairs of tags.
             * @return This builder.
             */
            public Builder tags(String... tags) {
                return tags(Tags.of(tags));
            }

            /**
             * @param tags Tags to add to the multiple indicator.
             * @return The builder with added tags.
             */
            public Builder tags(Iterable<Tag> tags) {
                this.tags = this.tags.and(tags);
                return this;
            }

            /**
             * @param key The tag key.
             * @param value The tag value.
             * @return The builder with a single added tag.
             */
            public Builder tag(String key, String value) {
                this.tags = tags.and(key, value);
                return this;
            }

            public final MultipleIndicator and() {
                return new MultipleIndicator(name, tags, failedMessage, objectives, (o1, o2) -> o1 && o2);
            }

            public final MultipleIndicator or() {
                return new MultipleIndicator(name, tags, failedMessage, objectives, (o1, o2) -> o1 || o2);
            }

            /**
             * Combine {@link ServiceLevelObjective ServiceLevelObjectives} with the
             * provided {@code combiner}.
             * @param combiner combiner to combine {@link ServiceLevelObjective
             * ServiceLevelObjectives}
             * @return combined {@code MultipleIndicator}
             * @since 1.6.5
             */
            public final MultipleIndicator combine(BinaryOperator<Boolean> combiner) {
                return new MultipleIndicator(name, tags, failedMessage, objectives, combiner);
            }

        }

    }

    static class FilteredServiceLevelObjective extends ServiceLevelObjective {

        private final ServiceLevelObjective delegate;

        FilteredServiceLevelObjective(Meter.Id id, ServiceLevelObjective delegate) {
            super(id.getName(), Tags.of(id.getTags()), id.getBaseUnit(), id.getDescription());
            this.delegate = delegate;
        }

        @Override
        public Collection<MeterBinder> getRequires() {
            return delegate.getRequires();
        }

        @Override
        public Collection<MeterFilter> getAcceptFilters() {
            return delegate.getAcceptFilters();
        }

        @Override
        public void tick(MeterRegistry registry) {
            delegate.tick(registry);
        }

        @Override
        public boolean healthy(MeterRegistry registry) {
            return delegate.healthy(registry);
        }

    }

}
