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
package io.micrometer.core.tck;

import io.micrometer.core.Issue;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.core.instrument.Statistic.ACTIVE_TASKS;
import static io.micrometer.core.instrument.Statistic.DURATION;
import static io.micrometer.core.instrument.util.TimeUtils.millisToUnit;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Base class for {@link MeterRegistry} compatibility tests. To run a
 * {@link MeterRegistry} implementation against this TCK, make a test class that extends
 * this and implement the abstract methods.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
public abstract class MeterRegistryCompatibilityKit {

    // Retain this as a member field to prevent it to be garbage-collected in OpenJ9.
    private final Object o = new Object();

    protected MeterRegistry registry;

    protected ObservationRegistry observationRegistry = ObservationRegistry.create();

    public abstract MeterRegistry registry();

    public abstract Duration step();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in
        // their registry() implementation
        registry = registry();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
    }

    @Test
    @DisplayName("compatibility test provides a non-null registry instance")
    void registryIsNotNull() {
        assertThat(registry).isNotNull();
    }

    @Test
    @DisplayName("meters with the same name and tags are registered once")
    void uniqueMeters() {
        registry.counter("foo");
        registry.counter("foo");

        assertThat(registry.get("foo").meters().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("find meters by name and class type matching a subset of their tags")
    void findMeters() {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c1);
        assertThat(registry.get("bar").tags("k", "v").counter()).isSameAs(c2);
    }

    @Test
    @DisplayName("find meters by name and type matching a subset of their tags")
    void findMetersByType() {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c1);
        assertThat(registry.get("bar").tags("k", "v").counter()).isSameAs(c2);
    }

    @Test
    @DisplayName("find meters by name and value")
    void findMetersByValue() {
        Counter c = registry.counter("counter");
        c.increment();

        Timer t = registry.timer("timer");
        t.record(10, TimeUnit.NANOSECONDS);

        clock(registry).add(step());

        assertThat(registry.get("counter").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("timer").timer().count()).isEqualTo(1L);
        assertThat(registry.get("timer").timer().totalTime(TimeUnit.NANOSECONDS)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("common tags are added to every measurement")
    void addCommonTags() {
        registry.config().commonTags("k", "v");
        Counter c = registry.counter("foo");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c);
        assertThat(c.getId().getTagsAsIterable()).hasSize(1);
    }

    @Test
    @DisplayName("original and convention names are preserved for custom meter types")
    void aTaleOfTwoNames() {
        AtomicInteger n = new AtomicInteger(1);
        registry.more().counter("my.counter", Collections.emptyList(), n);
        registry.get("my.counter").functionCounter();
    }

    @Test
    @DisplayName("function timers respect the base unit of an underlying registry")
    void functionTimerUnits() {
        registry.more().timer("function.timer", emptyList(), this.o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer ft = registry.get("function.timer").functionTimer();
        clock(registry).add(step());
        assertThat(ft.measure()).anySatisfy(ms -> {
            TimeUnit baseUnit = TimeUnit.valueOf(requireNonNull(ft.getId().getBaseUnit()).toUpperCase(Locale.ROOT));
            assertThat(ms.getStatistic()).isEqualTo(Statistic.TOTAL_TIME);
            assertThat(TimeUtils.convert(ms.getValue(), baseUnit, TimeUnit.MILLISECONDS)).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("meters with synthetics can be removed without causing deadlocks")
    void removeMeterWithSynthetic() {
        Timer timer = Timer.builder("my.timer")
            .publishPercentiles(0.95)
            .serviceLevelObjectives(Duration.ofMillis(10))
            .register(registry);

        registry.remove(timer);
    }

    @DisplayName("counters")
    @Nested
    class CounterTck {

        @DisplayName("multiple increments are maintained")
        @Test
        void increment() {
            Counter c = registry.counter("myCounter");
            c.increment();
            clock(registry).add(step());
            assertThat(c.count()).isCloseTo(1.0, offset(1e-12));
            c.increment();
            c.increment();
            clock(registry).add(step());

            // in the case of a step aggregating system will be 2, otherwise 3
            assertThat(c.count()).isGreaterThanOrEqualTo(2.0);
        }

        @Test
        @DisplayName("increment by a non-negative amount")
        void incrementAmount() {
            Counter c = registry.counter("myCounter");
            c.increment(2);
            c.increment(0);
            clock(registry).add(step());

            assertThat(c.count()).isEqualTo(2L);
        }

        @Test
        @DisplayName("function-tracking counter increments by change in a monotonically increasing function when observed")
        void functionTrackingCounter() {
            AtomicLong n = new AtomicLong();
            registry.more().counter("tracking", emptyList(), n);
            n.incrementAndGet();

            clock(registry).add(step());
            registry.forEachMeter(Meter::measure);
            assertThat(registry.get("tracking").functionCounter().count()).isEqualTo(1.0);
        }

    }

    @DisplayName("distribution summaries")
    @Nested
    class DistributionSummaryTck {

        @Test
        @DisplayName("multiple recordings are maintained")
        void record() {
            DistributionSummary ds = registry.summary("my.summary");

            ds.record(10);
            clock(registry).add(step());

            ds.count();

            assertSoftly(softly -> {
                softly.assertThat(ds.count()).isEqualTo(1L);
                softly.assertThat(ds.totalAmount()).isEqualTo(10L);
            });

            ds.record(10);
            ds.record(10);
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(ds.count()).isGreaterThanOrEqualTo(2L);
                softly.assertThat(ds.totalAmount()).isGreaterThanOrEqualTo(20L);
            });
        }

        @Test
        @DisplayName("negative quantities are ignored")
        void recordNegative() {
            DistributionSummary ds = registry.summary("my.summary");

            ds.record(-10);
            assertSoftly(softly -> {
                softly.assertThat(ds.count()).isEqualTo(0L);
                softly.assertThat(ds.totalAmount()).isEqualTo(0L);
            });
        }

        @Test
        @DisplayName("record zero")
        void recordZero() {
            DistributionSummary ds = registry.summary("my.summary");

            ds.record(0);
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(ds.count()).isEqualTo(1L);
                softly.assertThat(ds.totalAmount()).isEqualTo(0L);
            });
        }

        @Test
        @DisplayName("scale samples by a fixed factor")
        void scale() {
            DistributionSummary ds = DistributionSummary.builder("my.summary").scale(2.0).register(registry);

            ds.record(1);

            clock(registry).add(step());
            assertThat(ds.totalAmount()).isEqualTo(2.0);
        }

        @SuppressWarnings("deprecation")
        @Test
        void percentiles() {
            DistributionSummary s = DistributionSummary.builder("my.summary").publishPercentiles(1).register(registry);

            s.record(1);
            assertThat(s.percentile(1)).isCloseTo(1, offset(0.3));
            assertThat(s.percentile(0.5)).isNaN();
        }

        @SuppressWarnings("deprecation")
        @Test
        void histogramCounts() {
            DistributionSummary s = DistributionSummary.builder("my.summmary")
                .serviceLevelObjectives(1.0)
                .register(registry);

            // ensure time-window based histograms are not fully rotated when we assert
            Duration halfStep = step().dividedBy(2);
            clock(registry).add(halfStep);
            s.record(1);
            // accommodate StepBucketHistogram
            clock(registry).add(halfStep);
            assertThat(s.histogramCountAtValue(1)).isEqualTo(1);
            assertThat(s.histogramCountAtValue(2)).isNaN();
        }

        @Issue("#3904")
        @Test
        void histogramCountsPublishPercentileHistogramAndSlos() {
            DistributionSummary summary = DistributionSummary.builder("my.summmary")
                .serviceLevelObjectives(5, 50, 95)
                .publishPercentileHistogram()
                .register(registry);

            // ensure time-window based histograms are not fully rotated when we assert
            Duration halfStep = step().dividedBy(2);
            clock(registry).add(halfStep);

            for (int val : new int[] { 22, 55, 66, 98 }) {
                summary.record(val);
            }
            // accommodate StepBucketHistogram
            clock(registry).add(halfStep);

            HistogramSnapshot snapshot = summary.takeSnapshot();
            CountAtBucket[] countAtBuckets = snapshot.histogramCounts();

            assertHistogramBuckets(countAtBuckets);
        }

    }

    private void assertHistogramBuckets(CountAtBucket[] countAtBuckets) {
        assertHistogramBuckets(countAtBuckets, null);
    }

    private void assertHistogramBuckets(CountAtBucket[] countAtBuckets, TimeUnit timeUnit) {
        // percentile histogram buckets may be there, assert SLO buckets are present
        assertThat(countAtBuckets).extracting(c -> getCount(c, timeUnit)).contains(5.0, 50.0, 95.0);

        assertThat(countAtBuckets).satisfiesAnyOf(
                // we can directly check the count of cumulative SLO buckets
                bucketCounts -> assertThat(Arrays.stream(bucketCounts)
                    .filter(countAtBucket -> Arrays.asList(5.0, 50.0, 95.0)
                        .contains(getCount(countAtBucket, timeUnit))))
                    .extracting(CountAtBucket::count)
                    .containsExactly(0.0, 1.0, 3.0),
                // if not cumulative buckets, we need to add up buckets in range.
                bucketCounts -> {
                    assertThat(nonCumulativeBucketCountForRange(bucketCounts, timeUnit, 0, 5)).isEqualTo(0);
                    assertThat(nonCumulativeBucketCountForRange(bucketCounts, timeUnit, 5, 50)).isEqualTo(1);
                    assertThat(nonCumulativeBucketCountForRange(bucketCounts, timeUnit, 50, 95)).isEqualTo(2);
                });
    }

    private double getCount(CountAtBucket countAtBucket, TimeUnit timeUnit) {
        return timeUnit != null ? countAtBucket.bucket(timeUnit) : countAtBucket.bucket();
    }

    private double nonCumulativeBucketCountForRange(CountAtBucket[] countAtBuckets, TimeUnit timeUnit,
            double exclusiveMinBucket, double inclusiveMaxBucket) {
        double count = 0;
        for (CountAtBucket countAtBucket : countAtBuckets) {
            double c = getCount(countAtBucket, timeUnit);
            if (c > exclusiveMinBucket && c <= inclusiveMaxBucket) {
                count += countAtBucket.count();
            }
        }
        return count;
    }

    @DisplayName("gauges")
    @Nested
    class GaugeTck {

        @Test
        @DisplayName("gauges attached to a number are updated when their values are observed")
        void numericGauge() {
            AtomicInteger n = registry.gauge("my.gauge", new AtomicInteger());
            n.set(1);

            Gauge g = registry.get("my.gauge").gauge();
            assertThat(g.value()).isEqualTo(1);

            n.set(2);
            assertThat(g.value()).isEqualTo(2);
        }

        @Test
        @DisplayName("gauges attached to an object are updated when their values are observed")
        void objectGauge() {
            List<String> list = registry.gauge("my.gauge", emptyList(), new ArrayList<>(), List::size);
            list.addAll(Arrays.asList("a", "b"));

            Gauge g = registry.get("my.gauge").gauge();
            assertThat(g.value()).isEqualTo(2);
        }

        @Test
        @DisplayName("gauges can be directly associated with collection size")
        void collectionSizeGauge() {
            List<String> list = registry.gaugeCollectionSize("my.gauge", emptyList(), new ArrayList<>());
            list.addAll(Arrays.asList("a", "b"));

            Gauge g = registry.get("my.gauge").gauge();
            assertThat(g.value()).isEqualTo(2);
        }

        @Test
        @DisplayName("gauges can be directly associated with map entry size")
        void mapSizeGauge() {
            Map<String, Integer> map = registry.gaugeMapSize("my.gauge", emptyList(), new HashMap<>());
            map.put("a", 1);

            Gauge g = registry.get("my.gauge").gauge();
            assertThat(g.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("gauges that reference an object that is garbage collected report NaN")
        void garbageCollectedSourceObject() {
            registry.gauge("my.gauge", emptyList(), (Map) null, Map::size);
            assertThat(registry.get("my.gauge").gauge().value())
                .matches(val -> val == null || Double.isNaN(val) || val == 0.0);
        }

        @Test
        @DisplayName("strong reference gauges")
        void strongReferenceGauges() {
            Gauge.builder("weak.ref", 1.0, n -> n).register(registry);
            Gauge.builder("strong.ref", 1.0, n -> n).strongReference(true).register(registry);

            System.gc();

            assertThat(registry.get("weak.ref").gauge().value()).isNaN();
            assertThat(registry.get("strong.ref").gauge().value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("gauges cannot be registered twice")
        void gaugesCannotBeRegisteredTwice() {
            AtomicInteger n1 = registry.gauge("my.gauge", new AtomicInteger(1));
            AtomicInteger n2 = registry.gauge("my.gauge", new AtomicInteger(2));

            assertThat(registry.get("my.gauge").gauges()).hasSize(1);
            assertThat(registry.get("my.gauge").gauge().value()).isEqualTo(1);
            assertThat(n1).isNotNull().hasValue(1);
            assertThat(n2).isNotNull().hasValue(2);
        }

        @Test
        @DisplayName("gauges cannot be registered effectively twice")
        void gaugesCannotBeRegisteredEffectivelyTwice() {
            registry.config().meterFilter(MeterFilter.ignoreTags("ignored"));
            AtomicInteger n1 = registry.gauge("my.gauge", Tags.of("ignored", "1"), new AtomicInteger(1));
            AtomicInteger n2 = registry.gauge("my.gauge", Tags.of("ignored", "2"), new AtomicInteger(2));

            assertThat(registry.get("my.gauge").gauges()).hasSize(1);
            assertThat(registry.get("my.gauge").gauge().value()).isEqualTo(1);
            assertThat(n1).isNotNull().hasValue(1);
            assertThat(n2).isNotNull().hasValue(2);
        }

    }

    @DisplayName("long task timers")
    @Nested
    class LongTaskTimerTck {

        @Test
        @DisplayName("total time is preserved for a single timing")
        void record() {
            LongTaskTimer t = registry.more().longTaskTimer("my.timer");

            LongTaskTimer.Sample sample = t.start();
            clock(registry).add(10, TimeUnit.NANOSECONDS);

            assertSoftly(softly -> {
                softly.assertThat(t.duration(TimeUnit.NANOSECONDS)).isEqualTo(10);
                softly.assertThat(t.duration(TimeUnit.MICROSECONDS)).isEqualTo(0.01);
                softly.assertThat(sample.duration(TimeUnit.NANOSECONDS)).isEqualTo(10);
                softly.assertThat(sample.duration(TimeUnit.MICROSECONDS)).isEqualTo(0.01);
                softly.assertThat(t.activeTasks()).isEqualTo(1);
            });

            assertThat(t.measure()).satisfiesExactlyInAnyOrder(measurement -> assertThat(measurement).satisfies(m -> {
                assertThat(m.getValue()).isEqualTo(1.0);
                assertThat(m.getStatistic()).isSameAs(ACTIVE_TASKS);
            }), measurement -> assertThat(measurement).satisfies(m -> {
                assertThat(m.getValue()).isEqualTo(TimeUtils.convert(10, TimeUnit.NANOSECONDS, t.baseTimeUnit()));
                assertThat(m.getStatistic()).isSameAs(DURATION);
            }));

            clock(registry).add(10, TimeUnit.NANOSECONDS);
            sample.stop();

            assertSoftly(softly -> {
                softly.assertThat(t.duration(TimeUnit.NANOSECONDS)).isEqualTo(0);
                softly.assertThat(sample.duration(TimeUnit.NANOSECONDS)).isEqualTo(-1);
                softly.assertThat(t.activeTasks()).isEqualTo(0);
            });
        }

        @Test
        @DisplayName("supports sending the Nth percentile active task duration")
        void percentiles() {
            double[] rawPercentiles = new double[] { 0.5, 0.7, 0.91, 0.999, 1 };
            LongTaskTimer t = LongTaskTimer.builder("my.timer").publishPercentiles(rawPercentiles).register(registry);

            // Using the example of percentile interpolation from
            // https://statisticsbyjim.com/basics/percentiles/
            List<Integer> samples = Arrays.asList(48, 42, 40, 35, 22, 16, 13, 8, 6, 4, 2);
            int prior = samples.get(0);
            for (Integer value : samples) {
                clock(registry).add(prior - value, TimeUnit.SECONDS);
                t.start();
                prior = value;
            }
            clock(registry).add(samples.get(samples.size() - 1), TimeUnit.SECONDS);

            assertThat(t.activeTasks()).isEqualTo(11);

            ValueAtPercentile[] percentiles = t.takeSnapshot().percentileValues();

            int percentilesChecked = 0;
            for (ValueAtPercentile percentile : percentiles) {
                if (percentile.percentile() == 0.5) {
                    assertThat(percentile.value(TimeUnit.SECONDS)).isEqualTo(16);
                    percentilesChecked++;
                }
                else if (percentile.percentile() == 0.7) {
                    assertThat(percentile.value(TimeUnit.SECONDS)).isEqualTo(37, within(0.001));
                    percentilesChecked++;
                }
                else if (percentile.percentile() == 0.91) {
                    // a value close-to the highest value that is available for
                    // interpolation (11
                    // / 12)
                    assertThat(percentile.value(TimeUnit.SECONDS)).isEqualTo(47.5, within(0.1));
                    percentilesChecked++;
                }
                else if (percentile.percentile() == 0.999) {
                    assertThat(percentile.value(TimeUnit.SECONDS)).isEqualTo(48, within(0.1));
                    percentilesChecked++;
                }
                else if (percentile.percentile() == 1) {
                    assertThat(percentile.value(TimeUnit.SECONDS)).isEqualTo(48);
                    percentilesChecked++;
                }
            }

            // ensure all percentiles specified have been checked.
            assertThat(percentilesChecked).isEqualTo(rawPercentiles.length);
        }

        @Test
        @DisplayName("supports sending histograms of active task duration")
        void histogram() {
            LongTaskTimer t = LongTaskTimer.builder("my.timer")
                .serviceLevelObjectives(Duration.ofSeconds(10), Duration.ofSeconds(40), Duration.ofMinutes(1))
                .register(registry);

            List<Integer> samples = Arrays.asList(48, 42, 40, 35, 22, 16, 13, 8, 6, 4, 2);
            int prior = samples.get(0);
            for (Integer value : samples) {
                clock(registry).add(prior - value, TimeUnit.SECONDS);
                t.start();
                prior = value;
            }
            clock(registry).add(samples.get(samples.size() - 1), TimeUnit.SECONDS);

            CountAtBucket[] countAtBuckets = t.takeSnapshot().histogramCounts();

            assertThat(countAtBuckets[0].bucket(TimeUnit.SECONDS)).isEqualTo(10);
            assertThat(countAtBuckets[0].count()).isEqualTo(4);

            assertThat(countAtBuckets[1].bucket(TimeUnit.SECONDS)).isEqualTo(40);
            assertThat(countAtBuckets[1].count()).isEqualTo(9);

            assertThat(countAtBuckets[2].bucket(TimeUnit.MINUTES)).isEqualTo(1);
            assertThat(countAtBuckets[2].count()).isEqualTo(11);
        }

        @Test
        @DisplayName("attributes from @Timed annotation apply to builder")
        void timedAnnotation() {
            Timed timed = AnnotationHolder.class.getAnnotation(Timed.class);
            LongTaskTimer ltt = LongTaskTimer.builder(timed).register(registry);
            Meter.Id id = ltt.getId();
            assertThat(id.getName()).isEqualTo("my.name");
            assertThat(id.getTags()).containsExactly(Tag.of("a", "tag"));
            assertThat(id.getDescription()).isEqualTo("some description");
            if (ltt instanceof CumulativeHistogramLongTaskTimer) {
                assertThat(ltt.takeSnapshot().histogramCounts()).isNotEmpty();
            }
        }

        @Timed(value = "my.name", longTask = true, extraTags = { "a", "tag" }, description = "some description",
                histogram = true)
        class AnnotationHolder {

        }

    }

    @DisplayName("timers")
    @Nested
    class TimerTck {

        @DisplayName("autocloseable sample")
        @ParameterizedTest(name = "when outcome is \"{0}\"")
        @CsvSource({ "success", "error" })
        @Issue("#1425")
        void closeable(String outcome) {
            try (Timer.ResourceSample sample = Timer.resource(registry, "requests")
                .description("This is an operation")
                .publishPercentileHistogram()) {
                try {
                    if (outcome.equals("error")) {
                        throw new IllegalArgumentException("boom");
                    }
                    sample.tag("outcome", "success");
                }
                catch (Throwable t) {
                    sample.tag("outcome", "error");
                }
            }

            clock(registry).add(step());
            assertThat(registry.get("requests").tag("outcome", outcome).timer().count()).isEqualTo(1);
        }

        @DisplayName("record callable")
        @Test
        void recordCallable() throws Exception {
            registry.timer("timer").recordCallable(() -> "");
        }

        @Test
        @DisplayName("total time and count are preserved for a single timing")
        void record() {
            Timer t = registry.timer("myTimer");
            t.record(42, TimeUnit.MILLISECONDS);
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(t.count()).isEqualTo(1L);
                softly.assertThat(t.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(42, offset(1.0e-12));
            });
        }

        @Test
        @DisplayName("record durations")
        void recordDuration() {
            Timer t = registry.timer("myTimer");
            t.record(Duration.ofMillis(42));
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(t.count()).isEqualTo(1L);
                softly.assertThat(t.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(42, offset(1.0e-12));
            });
        }

        @Test
        @DisplayName("negative times are discarded by the Timer")
        void recordNegative() {
            Timer t = registry.timer("myTimer");
            t.record(-42, TimeUnit.MILLISECONDS);

            assertSoftly(softly -> {
                softly.assertThat(t.count()).isEqualTo(0L);
                softly.assertThat(t.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(0, offset(1.0e-12));
            });
        }

        @Test
        @DisplayName("zero times contribute to the count of overall events but do not add to total time")
        void recordZero() {
            Timer t = registry.timer("myTimer");
            t.record(0, TimeUnit.MILLISECONDS);
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(t.count()).isEqualTo(1L);
                softly.assertThat(t.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(0d);
            });
        }

        @Test
        @DisplayName("record a runnable task")
        void recordWithRunnable() {
            Timer t = registry.timer("myTimer");

            Runnable r = () -> {
                clock(registry).add(10, TimeUnit.NANOSECONDS);
            };
            try {
                t.record(r);
                clock(registry).add(step());
            }
            finally {
                assertSoftly(softly -> {
                    softly.assertThat(t.count()).isEqualTo(1L);
                    softly.assertThat(t.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
                });
            }
        }

        @Test
        @DisplayName("record supplier")
        void recordWithSupplier() {
            Timer t = registry.timer("myTimer");
            String expectedResult = "response";
            Supplier<String> supplier = () -> {
                clock(registry).add(10, TimeUnit.NANOSECONDS);
                return expectedResult;
            };
            try {
                String supplierResult = t.record(supplier);
                assertThat(supplierResult).isEqualTo(expectedResult);
                clock(registry).add(step());
            }
            finally {
                assertSoftly(softly -> {
                    softly.assertThat(t.count()).isEqualTo(1L);
                    softly.assertThat(t.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
                });
            }
        }

        @Test
        @DisplayName("wrap supplier")
        void wrapSupplier() {
            Timer timer = registry.timer("myTimer");
            String expectedResult = "response";
            Supplier<String> supplier = () -> {
                clock(registry).add(10, TimeUnit.NANOSECONDS);
                return expectedResult;
            };
            try {
                Supplier<String> wrappedSupplier = timer.wrap(supplier);
                assertThat(wrappedSupplier.get()).isEqualTo(expectedResult);
                clock(registry).add(step());
            }
            finally {
                assertSoftly(softly -> {
                    softly.assertThat(timer.count()).isEqualTo(1L);
                    softly.assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
                });
            }
        }

        @Test
        @DisplayName("record with stateful Sample instance")
        void recordWithSample() {
            Timer timer = registry.timer("myTimer");
            Timer.Sample sample = Timer.start(registry);

            clock(registry).add(10, TimeUnit.NANOSECONDS);
            sample.stop(timer);
            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(timer.count()).isEqualTo(1L);
                softly.assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
            });
        }

        @Test
        @DisplayName("record with stateful Observation instance")
        void recordWithObservation() {
            Observation observation = Observation.createNotStarted("myObservation", observationRegistry)
                .lowCardinalityKeyValue("staticTag", "42")
                .start();

            // created after start, LongTaskTimer won't have it
            observation.lowCardinalityKeyValue("dynamicTag", "24");

            clock(registry).add(1, TimeUnit.SECONDS);
            observation.event(Observation.Event.of("testEvent", "event for testing"));

            LongTaskTimer longTaskTimer = registry.more().longTaskTimer("myObservation.active", "staticTag", "42");
            assertThat(longTaskTimer.activeTasks()).isEqualTo(1);

            observation.stop();
            clock(registry).add(step());

            assertThat(longTaskTimer.activeTasks()).isEqualTo(0);

            Timer timer = registry.timer("myObservation", "error", "none", "staticTag", "42", "dynamicTag", "24");
            assertSoftly(softly -> {
                softly.assertThat(timer.count()).isEqualTo(1L);
                softly.assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(1, offset(1.0e-12));
            });

            Counter counter = registry.counter("myObservation.testEvent", "staticTag", "42", "dynamicTag", "24");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("record with stateful Observation and Scope instances")
        void recordWithObservationAndScope() {
            Observation observation = Observation.start("myObservation", observationRegistry);
            try (Observation.Scope scope = observation.openScope()) {
                assertThat(scope.getCurrentObservation()).isSameAs(observation);
                clock(registry).add(10, TimeUnit.NANOSECONDS);
                observation.event(Observation.Event.of("testEvent", "event for testing"));
            }
            observation.stop();
            clock(registry).add(step());

            Timer timer = registry.timer("myObservation", "error", "none");
            assertSoftly(softly -> {
                softly.assertThat(timer.count()).isEqualTo(1L);
                softly.assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
            });

            Counter counter = registry.counter("myObservation.testEvent");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void recordMax() {
            Timer timer = registry.timer("my.timer");
            timer.record(10, TimeUnit.MILLISECONDS);
            timer.record(1, TimeUnit.SECONDS);

            clock(registry).add(step()); // for Atlas, which is step rather than
                                         // ring-buffer based
            assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);

            // noinspection ConstantConditions
            clock(registry)
                .add(Duration.ofMillis(step().toMillis() * DistributionStatisticConfig.DEFAULT.getBufferLength()));
            assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("callable task that throws exception is still recorded")
        void recordCallableException() {
            Timer t = registry.timer("myTimer");

            assertThatException().isThrownBy(() -> {
                t.recordCallable(() -> {
                    clock(registry).add(10, TimeUnit.NANOSECONDS);
                    throw new Exception("uh oh");
                });
            });

            clock(registry).add(step());

            assertSoftly(softly -> {
                softly.assertThat(t.count()).isEqualTo(1L);
                softly.assertThat(t.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(10, offset(1.0e-12));
            });
        }

        @SuppressWarnings("deprecation")
        @Test
        void percentiles() {
            Timer t = Timer.builder("my.timer").publishPercentiles(1).register(registry);

            t.record(1, TimeUnit.MILLISECONDS);
            assertThat(t.percentile(1, TimeUnit.MILLISECONDS)).isCloseTo(1, offset(0.3));
            assertThat(t.percentile(0.5, TimeUnit.MILLISECONDS)).isNaN();
        }

        @SuppressWarnings("deprecation")
        @Test
        void histogramCounts() {
            Timer t = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);

            // ensure time-window based histograms are not fully rotated when we assert
            Duration halfStep = step().dividedBy(2);
            clock(registry).add(halfStep);
            t.record(1, TimeUnit.MILLISECONDS);
            // accommodate StepBucketHistogram
            clock(registry).add(halfStep);
            assertThat(t.histogramCountAtValue((long) millisToUnit(1, TimeUnit.NANOSECONDS))).isEqualTo(1);
            assertThat(t.histogramCountAtValue(1)).isNaN();
        }

        @Issue("#3904")
        @Test
        void histogramCountsPublishPercentileHistogramAndSlos() {
            Timer timer = Timer.builder("my.timer")
                .serviceLevelObjectives(Duration.ofMillis(5), Duration.ofMillis(50), Duration.ofMillis(95))
                .publishPercentileHistogram()
                .register(registry);

            // ensure time-window based histograms are not fully rotated when we assert
            Duration halfStep = step().dividedBy(2);
            clock(registry).add(halfStep);

            for (int val : new int[] { 22, 55, 66, 98 }) {
                timer.record(Duration.ofMillis(val));
            }
            // accommodate StepBucketHistogram
            clock(registry).add(halfStep);

            HistogramSnapshot snapshot = timer.takeSnapshot();
            CountAtBucket[] countAtBuckets = snapshot.histogramCounts();

            assertHistogramBuckets(countAtBuckets, TimeUnit.MILLISECONDS);
        }

    }

}
