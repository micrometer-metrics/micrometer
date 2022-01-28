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

import io.micrometer.api.instrument.*;
import io.micrometer.api.instrument.Timer;
import io.micrometer.core.Issue;
import io.micrometer.api.annotation.Timed;
import io.micrometer.api.instrument.distribution.CountAtBucket;
import io.micrometer.api.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.api.instrument.distribution.ValueAtPercentile;
import io.micrometer.api.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.api.instrument.util.TimeUtils;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static io.micrometer.api.instrument.MockClock.clock;
import static io.micrometer.api.instrument.Statistic.ACTIVE_TASKS;
import static io.micrometer.api.instrument.Statistic.DURATION;
import static io.micrometer.api.instrument.util.TimeUtils.millisToUnit;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Base class for {@link MeterRegistry} compatibility tests.
 * To run a {@link MeterRegistry} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public abstract class MeterRegistryCompatibilityKit {

    // Retain this as a member field to prevent it to be garbage-collected in OpenJ9.
    private final Object o = new Object();

    protected MeterRegistry registry;

    public abstract MeterRegistry registry();
    public abstract Duration step();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
        registry = registry();
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
        registry.more().timer("function.timer", emptyList(),
            this.o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer ft = registry.get("function.timer").functionTimer();
        clock(registry).add(step());
        assertThat(ft.measure())
            .anySatisfy(ms -> {
                TimeUnit baseUnit = TimeUnit.valueOf(requireNonNull(ft.getId().getBaseUnit()).toUpperCase());
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
            assertThat(c.count()).isEqualTo(1.0, offset(1e-12));
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

            assertEquals(2L, c.count());
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

            assertAll(() -> assertEquals(1L, ds.count()),
                    () -> assertEquals(10L, ds.totalAmount()));

            ds.record(10);
            ds.record(10);
            clock(registry).add(step());

            assertAll(() -> assertTrue(ds.count() >= 2L),
                    () -> assertTrue(ds.totalAmount() >= 20L));
        }

        @Test
        @DisplayName("negative quantities are ignored")
        void recordNegative() {
            DistributionSummary ds = registry.summary("my.summary");

            ds.record(-10);
            assertAll(() -> assertEquals(0, ds.count()),
                    () -> assertEquals(0L, ds.totalAmount()));
        }

        @Test
        @DisplayName("record zero")
        void recordZero() {
            DistributionSummary ds = registry.summary("my.summary");

            ds.record(0);
            clock(registry).add(step());

            assertAll(() -> assertEquals(1L, ds.count()),
                    () -> assertEquals(0L, ds.totalAmount()));
        }

        @Test
        @DisplayName("scale samples by a fixed factor")
        void scale() {
            DistributionSummary ds = DistributionSummary.builder("my.summary")
                    .scale(2.0)
                    .register(registry);

            ds.record(1);

            clock(registry).add(step());
            assertThat(ds.totalAmount()).isEqualTo(2.0);
        }

        @Deprecated
        @Test
        void percentiles() {
            DistributionSummary s = DistributionSummary.builder("my.summary")
                    .publishPercentiles(1)
                    .register(registry);

            s.record(1);
            assertThat(s.percentile(1)).isEqualTo(1, Offset.offset(0.3));
            assertThat(s.percentile(0.5)).isNaN();
        }

        @Deprecated
        @Test
        void histogramCounts() {
            DistributionSummary s = DistributionSummary.builder("my.summmary")
                    .serviceLevelObjectives(1.0)
                    .register(registry);

            s.record(1);
            assertThat(s.histogramCountAtValue(1)).isEqualTo(1);
            assertThat(s.histogramCountAtValue(2)).isNaN();
        }
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
            assertThat(registry.get("my.gauge").gauge().value()).matches(val -> val == null || Double.isNaN(val) || val == 0.0);
        }

        @Test
        @DisplayName("strong reference gauges")
        void strongReferenceGauges() {
            Gauge.builder("weak.ref", 1.0, n -> n).register(registry);
            Gauge.builder("strong.ref", 1.0, n -> n)
                    .strongReference(true)
                    .register(registry);

            System.gc();

            assertThat(registry.get("weak.ref").gauge().value()).isNaN();
            assertThat(registry.get("strong.ref").gauge().value()).isEqualTo(1.0);
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

            assertAll(() -> assertEquals(10, t.duration(TimeUnit.NANOSECONDS)),
                    () -> assertEquals(0.01, t.duration(TimeUnit.MICROSECONDS)),
                    () -> assertEquals(10, sample.duration(TimeUnit.NANOSECONDS)),
                    () -> assertEquals(0.01, sample.duration(TimeUnit.MICROSECONDS)),
                    () -> assertEquals(1, t.activeTasks()));

            assertThat(t.measure()).satisfiesExactlyInAnyOrder(
                    measurement -> assertThat(measurement).satisfies(m -> {
                        assertThat(m.getValue()).isEqualTo(1.0);
                        assertThat(m.getStatistic()).isSameAs(ACTIVE_TASKS);
                    }),
                    measurement -> assertThat(measurement).satisfies(m -> {
                        assertThat(m.getValue()).isEqualTo(TimeUtils.convert(10, TimeUnit.NANOSECONDS, t.baseTimeUnit()));
                        assertThat(m.getStatistic()).isSameAs(DURATION);
                    })
            );

            clock(registry).add(10, TimeUnit.NANOSECONDS);
            sample.stop();

            assertAll(() -> assertEquals(0, t.duration(TimeUnit.NANOSECONDS)),
                    () -> assertEquals(-1, sample.duration(TimeUnit.NANOSECONDS)),
                    () -> assertEquals(0, t.activeTasks()));
        }

        @Test
        @DisplayName("supports sending the Nth percentile active task duration")
        void percentiles() {
            LongTaskTimer t = LongTaskTimer.builder("my.timer")
                    .publishPercentiles(0.5, 0.7, 0.91, 0.999, 1)
                    .register(registry);

            // Using the example of percentile interpolation from https://statisticsbyjim.com/basics/percentiles/
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

            assertThat(percentiles[0].percentile()).isEqualTo(0.5);
            assertThat(percentiles[0].value(TimeUnit.SECONDS)).isEqualTo(16);

            assertThat(percentiles[1].percentile()).isEqualTo(0.7);
            assertThat(percentiles[1].value(TimeUnit.SECONDS)).isEqualTo(37, within(0.001));

            // a value close-to the highest value that is available for interpolation (11 / 12)
            assertThat(percentiles[2].percentile()).isEqualTo(0.91);
            assertThat(percentiles[2].value(TimeUnit.SECONDS)).isEqualTo(47.5, within(0.1));

            assertThat(percentiles[3].percentile()).isEqualTo(0.999);
            assertThat(percentiles[3].value(TimeUnit.SECONDS)).isEqualTo(48, within(0.1));

            assertThat(percentiles[4].percentile()).isEqualTo(1);
            assertThat(percentiles[4].value(TimeUnit.SECONDS)).isEqualTo(48);
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

        @Timed(value = "my.name", longTask = true, extraTags = {"a", "tag"},
                description = "some description", histogram = true)
        class AnnotationHolder { }
    }

    @DisplayName("timers")
    @Nested
    class TimerTck {
        @DisplayName("autocloseable sample")
        @ParameterizedTest(name = "when outcome is \"{0}\"")
        @CsvSource({"success", "error"})
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
                } catch (Throwable t) {
                    sample.tag("outcome", "error");
                }
            }

            clock(registry).add(step());
            assertThat(registry.get("requests").tag("outcome", outcome).timer().count())
                    .isEqualTo(1);
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

            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
        }

        @Test
        @DisplayName("record durations")
        void recordDuration() {
            Timer t = registry.timer("myTimer");
            t.record(Duration.ofMillis(42));
            clock(registry).add(step());

            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
        }

        @Test
        @DisplayName("negative times are discarded by the Timer")
        void recordNegative() {
            Timer t = registry.timer("myTimer");
            t.record(-42, TimeUnit.MILLISECONDS);

            assertAll(() -> assertEquals(0L, t.count()),
                    () -> assertEquals(0, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }

        @Test
        @DisplayName("zero times contribute to the count of overall events but do not add to total time")
        void recordZero() {
            Timer t = registry.timer("myTimer");
            t.record(0, TimeUnit.MILLISECONDS);
            clock(registry).add(step());

            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(0L, t.totalTime(TimeUnit.NANOSECONDS)));
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
            } finally {
                assertAll(() -> assertEquals(1L, t.count()),
                        () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
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
                assertEquals(expectedResult, supplierResult);
                clock(registry).add(step());
            } finally {
                assertAll(() -> assertEquals(1L, t.count()),
                        () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
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
                assertEquals(expectedResult, wrappedSupplier.get());
                clock(registry).add(step());
            } finally {
                assertAll(() -> assertEquals(1L, timer.count()),
                        () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
            }
        }

//        @Test
//        @DisplayName("record with stateful Observation instance")
//        void recordWithObservation() {
//            Timer.Sample sample = Timer.start(registry);
//            clock(registry).add(10, TimeUnit.NANOSECONDS);
//            sample.stop(Timer.builder("myTimer"));
//            clock(registry).add(step());
//
//            Timer timer = registry.timer("myTimer");
//            assertAll(() -> assertEquals(1L, timer.count()),
//                    () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
//        }

//        @Test
//        @DisplayName("record with stateful Observation and Scope instances")
//        void recordWithObservationAndScope() {
//            Timer.Sample sample = Timer.start(registry);
//            try (Timer.Scope scope = sample.makeCurrent()) {
//                assertThat(scope.getSample()).isSameAs(sample);
//                clock(registry).add(10, TimeUnit.NANOSECONDS);
//            }
//            sample.stop(Timer.builder("myTimer"));
//            clock(registry).add(step());
//
//            Timer timer = registry.timer("myTimer");
//            assertAll(() -> assertEquals(1L, timer.count()),
//                    () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
//        }

        @Test
        @DisplayName("record using handlers")
        void recordWithHandlers() {
            @SuppressWarnings("unchecked")
            ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
            @SuppressWarnings("unchecked")
            ObservationHandler<Observation.Context> handlerThatHandlesNothing = mock(ObservationHandler.class);
            registry.config().observationHandler(handler);
            registry.config().observationHandler(handlerThatHandlesNothing);
            when(handler.supportsContext(any())).thenReturn(true);
            when(handlerThatHandlesNothing.supportsContext(any())).thenReturn(false);

            Observation observation = registry.observation("myObservation");
            verify(handler).supportsContext(isA(Observation.Context.class));
            verify(handler).onStart(same(observation), isA(Observation.Context.class));
            verify(handlerThatHandlesNothing).supportsContext(isA(Observation.Context.class));
            verifyNoMoreInteractions(handlerThatHandlesNothing);

            try (Observation.Scope scope = observation.openScope()) {
                verify(handler).onScopeOpened(same(observation), isA(Observation.Context.class));
                assertThat(scope.getCurrentObservation()).isSameAs(observation);

                clock(registry).add(10, TimeUnit.NANOSECONDS);
                Throwable exception = new IOException("simulated");
                observation.error(exception);
                verify(handler).onError(same(observation), isA(Observation.Context.class));
            }
            verify(handler).onScopeClosed(same(observation), isA(Observation.Context.class));
            observation.stop();

            Timer timer = registry.timer("myTimer");
            verify(handler).onStop(same(observation), isA(Observation.Context.class));
            clock(registry).add(step());

            assertAll(() -> assertEquals(1L, timer.count()),
                    () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }

        @Test
        void recordMax() {
            Timer timer = registry.timer("my.timer");
            timer.record(10, TimeUnit.MILLISECONDS);
            timer.record(1, TimeUnit.SECONDS);

            clock(registry).add(step()); // for Atlas, which is step rather than ring-buffer based
            assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);

            //noinspection ConstantConditions
            clock(registry).add(Duration.ofMillis(step().toMillis() * DistributionStatisticConfig.DEFAULT.getBufferLength()));
            assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("callable task that throws exception is still recorded")
        void recordCallableException() {
            Timer t = registry.timer("myTimer");

            assertThrows(Exception.class, () -> {
                t.recordCallable(() -> {
                    clock(registry).add(10, TimeUnit.NANOSECONDS);
                    throw new Exception("uh oh");
                });
            });

            clock(registry).add(step());

            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }

        @Deprecated
        @Test
        void percentiles() {
            Timer t = Timer.builder("my.timer")
                    .publishPercentiles(1)
                    .register(registry);

            t.record(1, TimeUnit.MILLISECONDS);
            assertThat(t.percentile(1, TimeUnit.MILLISECONDS)).isEqualTo(1, Offset.offset(0.3));
            assertThat(t.percentile(0.5, TimeUnit.MILLISECONDS)).isNaN();
        }

        @Deprecated
        @Test
        void histogramCounts() {
            Timer t = Timer.builder("my.timer")
                    .serviceLevelObjectives(Duration.ofMillis(1))
                    .register(registry);

            t.record(1, TimeUnit.MILLISECONDS);
            assertThat(t.histogramCountAtValue((long) millisToUnit(1, TimeUnit.NANOSECONDS))).isEqualTo(1);
            assertThat(t.histogramCountAtValue(1)).isNaN();
        }
    }
}

