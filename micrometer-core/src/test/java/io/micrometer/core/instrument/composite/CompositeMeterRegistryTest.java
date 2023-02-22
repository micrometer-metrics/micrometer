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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CompositeMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class CompositeMeterRegistryTest {

    private MockClock clock = new MockClock();

    private CompositeMeterRegistry composite = new CompositeMeterRegistry();

    private SimpleMeterRegistry simple = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

    @Test
    void metricsAreInitiallyNoop() {
        // doesn't blow up
        composite.counter("counter").increment();
    }

    @DisplayName("base units on meters that support them are passed through underlying registries")
    @Test
    void baseUnitsPreserved() {
        composite.add(simple);

        Counter.builder("counter").baseUnit(BaseUnits.BYTES).register(composite);
        DistributionSummary.builder("summary").baseUnit(BaseUnits.BYTES).register(composite);
        Gauge.builder("gauge", new AtomicInteger(), AtomicInteger::get).baseUnit(BaseUnits.BYTES).register(composite);

        assertThat(simple.get("counter").counter().getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        assertThat(simple.get("summary").summary().getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        assertThat(simple.get("gauge").gauge().getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

    @DisplayName("metrics stop receiving updates when their registry parent is removed from a composite")
    @Test
    void metricAfterRegistryRemove() {
        composite.add(simple);

        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        Counter simpleCounter = simple.get("counter").counter();
        assertThat(simpleCounter.count()).isEqualTo(1);

        composite.remove(simple);
        compositeCounter.increment();

        // simple counter doesn't receive the increment after simple is removed from the
        // composite
        assertThat(simpleCounter.count()).isEqualTo(1);

        composite.add(simple);
        compositeCounter.increment();

        // now it receives updates again
        assertThat(simpleCounter.count()).isEqualTo(2);
    }

    @DisplayName("metrics that are created before a registry is added are later added to that registry")
    @Test
    void metricBeforeRegistryAdd() {
        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        // increments are being NOOPd until there is a registry in the composite
        assertThat(compositeCounter.count()).isEqualTo(0);

        composite.add(simple);

        compositeCounter.increment();

        assertThat(compositeCounter.count()).isEqualTo(1);

        // only the increment AFTER simple is added to the composite is counted to it
        assertThat(simple.get("counter").counter().count()).isEqualTo(1.0);
    }

    @DisplayName("metrics that are created after a registry is added to that registry")
    @Test
    void registryBeforeMetricAdd() {
        composite.add(simple);
        composite.counter("counter").increment();

        assertThat(simple.get("counter").counter().count()).isEqualTo(1.0);
    }

    @DisplayName("metrics follow the naming convention of each registry in the composite")
    @Test
    void namingConventions() {
        simple.config().namingConvention(NamingConvention.camelCase);

        composite.add(simple);
        composite.counter("my.counter").increment();

        assertThat(simple.get("my.counter").counter().count()).isEqualTo(1.0);
    }

    @DisplayName("common tags added to the composite affect meters registered with registries in the composite")
    @Test
    void commonTags() {
        simple.config().commonTags("instance", "local"); // added alongside other common
                                                         // tags in the composite
        simple.config().commonTags("region", "us-west-1"); // overridden by the composite

        composite.config().commonTags("region", "us-east-1");
        composite.add(simple);
        composite.config().commonTags("stack", "test");

        composite.counter("counter").increment();

        simple.get("counter").tags("region", "us-east-1", "stack", "test", "instance", "local").counter();
    }

    @DisplayName("function timer base units are delegated to registries in the composite")
    @Test
    void functionTimerUnits() {
        composite.add(simple);
        Object o = new Object();

        composite.more().timer("function.timer", emptyList(), o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer functionTimer = simple.get("function.timer").functionTimer();
        assertThat(functionTimer.count()).isEqualTo(1);
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1);
    }

    @Issue("#255")
    @Test
    void castingFunctionCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

        FunctionCounter.builder("foo", 1L, x -> x).register(compositeMeterRegistry);

        compositeMeterRegistry.add(registry);
    }

    @Test
    void differentFiltersForCompositeAndChild() {
        composite.add(simple);

        simple.config().meterFilter(MeterFilter.denyNameStartsWith("deny.child"));
        composite.config().meterFilter(MeterFilter.denyNameStartsWith("deny.composite"));

        composite.counter("deny.composite");
        composite.counter("deny.child");

        assertThat(simple.find("deny.composite").meter()).isNull();
        assertThat(composite.find("deny.composite").meter()).isNull();

        assertThat(simple.find("deny.child").meter()).isNull();
        composite.get("deny.child").meter();

        // if the meter is registered directly to the child, the composite config does not
        // take effect
        simple.counter("deny.composite");
        simple.get("deny.composite").meter();
    }

    @Test
    void histogramConfigDefaultIsNotAffectedByComposite() {
        composite.add(simple);

        // the expiry on this timer is determined by the simple registry's default
        // histogram config
        Timer t = Timer.builder("my.timer").distributionStatisticBufferLength(1).register(composite);

        t.record(1, TimeUnit.SECONDS);
        assertThat(t.max(TimeUnit.SECONDS)).isEqualTo(1.0);

        clock.add(SimpleConfig.DEFAULT.step());
        assertThat(t.max(TimeUnit.SECONDS)).isEqualTo(0.0);
    }

    @Test
    void compositePauseDetectorConfigOverridesChild() {
        composite.add(simple);

        CountDownLatch count = new CountDownLatch(1);
        composite.config().pauseDetector(new ClockDriftPauseDetector(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public Duration getPauseThreshold() {
                count.countDown();
                return super.getPauseThreshold();
            }
        });

        composite.timer("my.timer");
        assertThat(count.getCount()).isEqualTo(0);
    }

    @Issue("#546")
    @Test
    void metricsOnlyUpdatedOnceWhenChildIsPresentInCompositeHierarchyTwice() {
        composite.add(simple);

        CompositeMeterRegistry nested = new CompositeMeterRegistry();
        CompositeMeterRegistry nested2 = new CompositeMeterRegistry();
        nested2.add(simple);
        nested.add(nested2);
        nested.add(simple);

        composite.add(nested);

        Counter counter = composite.counter("my.counter");
        counter.increment();

        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void addRegistryInfluencesCompositeAncestry() {
        CompositeMeterRegistry nested = new CompositeMeterRegistry();
        CompositeMeterRegistry nested2 = new CompositeMeterRegistry();
        nested.add(nested2);
        composite.add(nested);

        nested2.add(simple);
        assertThat(composite.nonCompositeDescendants).containsExactly(simple);
    }

    @Test
    void removeRegistryInfluencesCompositeAncestry() {
        CompositeMeterRegistry nested = new CompositeMeterRegistry();
        CompositeMeterRegistry nested2 = new CompositeMeterRegistry();
        nested2.add(simple);
        nested.add(nested2);
        composite.add(nested);

        assertThat(composite.nonCompositeDescendants).containsExactly(simple);
        nested2.remove(simple);

        assertThat(composite.nonCompositeDescendants).isEmpty();
    }

    @Test
    void compositeCannotContainItself() {
        assertThatThrownBy(() -> composite.add(composite)).isInstanceOf(IllegalArgumentException.class);

        CompositeMeterRegistry nested = new CompositeMeterRegistry();
        nested.add(composite);
        assertThatThrownBy(() -> composite.add(nested)).isInstanceOf(IllegalArgumentException.class);

        assertThat(composite.getRegistries()).isEmpty();
    }

    @Issue("#704")
    @Test
    void noDeadlockOnAddingAndRemovingRegistries() throws InterruptedException {
        CompositeMeterRegistry composite2 = new CompositeMeterRegistry();
        composite.add(composite2);

        CountDownLatch latch = new CountDownLatch(1);
        Flux.range(0, 10000).parallel(8).doOnTerminate(latch::countDown).runOn(Schedulers.parallel()).subscribe(n -> {
            if (n % 2 == 0)
                composite2.add(simple);
            else
                composite2.remove(simple);
        });

        latch.await(10, TimeUnit.SECONDS);
        assertThat(latch.getCount()).isZero();
    }

    @Issue("#838")
    @Test
    void closeShouldCloseAllMeterRegistries() {
        MeterRegistry registry1 = mock(MeterRegistry.class);
        MeterRegistry registry2 = mock(MeterRegistry.class);

        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();
        compositeMeterRegistry.add(registry1);
        compositeMeterRegistry.add(registry2);

        compositeMeterRegistry.close();
        verify(registry1).close();
        verify(registry2).close();
    }

    private class CountingMeterRegistry extends StepMeterRegistry {

        Map<Meter.Id, Integer> publishCountById = new HashMap<>();

        public CountingMeterRegistry() {
            super(new StepRegistryConfig() {
                @Override
                public String prefix() {
                    return "test";
                }

                @Override
                public String get(String key) {
                    return null;
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            }, Clock.SYSTEM);
        }

        @Override
        public void publish() {
            for (Meter meter : getMeters()) {
                publishCountById.compute(meter.getId(), (l, count) -> count == null ? 1 : count + 1);
            }
        }

        public int count(Meter m) {
            return count(m.getId());
        }

        public int count(Meter.Id id) {
            return publishCountById.getOrDefault(id, 0);
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.MILLISECONDS;
        }

    }

    @Test
    void stopTrackingMetersThatAreRemoved() {
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        CountingMeterRegistry counting = new CountingMeterRegistry();
        registry.add(counting);

        Meter custom = Meter
            .builder("custom", Meter.Type.COUNTER, singletonList(new Measurement(() -> 1.0, Statistic.COUNT)))
            .register(registry);
        counting.publish();
        registry.remove(custom);
        counting.publish();
        assertThat(counting.count(custom)).isEqualTo(1);

        AtomicInteger tgObj = new AtomicInteger(1);
        registry.more()
            .timeGauge("timegauge", Tags.empty(), tgObj, TimeUnit.MILLISECONDS, AtomicInteger::incrementAndGet);
        TimeGauge timeGauge = registry.get("timegauge").timeGauge();
        counting.publish();
        registry.remove(timeGauge);
        counting.publish();
        assertThat(counting.count(timeGauge)).isEqualTo(1);

        AtomicInteger gaugeObj = new AtomicInteger(1);
        registry.gauge("gauge", gaugeObj, AtomicInteger::incrementAndGet);
        Gauge gauge = registry.get("gauge").gauge();
        counting.publish();
        registry.remove(gauge);
        counting.publish();
        assertThat(counting.count(gauge)).isEqualTo(1);

        Counter counter = registry.counter("counter");
        counter.increment();
        counting.publish();
        registry.remove(counter);
        counter.increment();
        counting.publish();
        assertThat(counting.count(counter)).isEqualTo(1);

        Timer timer = registry.timer("timer");
        timer.record(1, TimeUnit.MILLISECONDS);
        counting.publish();
        registry.remove(timer);
        timer.record(1, TimeUnit.MILLISECONDS);
        counting.publish();
        assertThat(counting.count(timer)).isEqualTo(1);

        DistributionSummary summary = registry.summary("summary");
        summary.record(1.0);
        counting.publish();
        registry.remove(summary);
        summary.record(1.0);
        counting.publish();
        assertThat(counting.count(summary)).isEqualTo(1);

        LongTaskTimer ltt = registry.more().longTaskTimer("ltt");
        ltt.start();
        counting.publish();
        registry.remove(ltt);
        counting.publish();
        assertThat(counting.count(ltt)).isEqualTo(1);

        AtomicInteger ftObj = new AtomicInteger(1);
        registry.more()
            .timer("functiontimer", Tags.empty(), ftObj, AtomicInteger::incrementAndGet, AtomicInteger::get,
                    TimeUnit.MILLISECONDS);
        FunctionTimer functionTimer = registry.get("functiontimer").functionTimer();
        counting.publish();
        registry.remove(functionTimer);
        counting.publish();
        assertThat(counting.count(functionTimer)).isEqualTo(1);

        AtomicInteger fcObj = new AtomicInteger(1);
        registry.more().counter("functioncounter", Tags.empty(), fcObj, AtomicInteger::incrementAndGet);
        FunctionCounter functionCounter = registry.get("functioncounter").functionCounter();
        counting.publish();
        registry.remove(functionCounter);
        counting.publish();
        assertThat(counting.count(functionCounter)).isEqualTo(1);
    }

    @RepeatedTest(100)
    void meterRegistrationShouldWorkConcurrently() throws InterruptedException {
        this.composite.add(this.simple);

        String meterName = "test.counter";
        String tagName = "test.tag";

        int count = 10000;
        int tagCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < count; i++) {
            int tagValue = i % tagCount;
            executor.execute(() -> {
                Counter counter = Counter.builder(meterName)
                    .tag(tagName, String.valueOf(tagValue))
                    .register(this.composite);
                counter.increment();
            });
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(1L, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < tagCount; i++) {
            assertThat(this.composite.find(meterName).tag(tagName, String.valueOf(i)).counter().count())
                .isEqualTo(count / tagCount);
        }
    }

    @Test
    @Issue("#2354")
    void meterRemovalPropagatesToChildRegistryWithModifyingFilter() {
        this.simple.config().commonTags("host", "localhost");
        this.composite.add(this.simple);

        Counter counter = this.composite.counter("my.counter");
        this.composite.remove(counter);

        assertThat(this.composite.getMeters()).isEmpty();
        assertThat(this.simple.getMeters()).isEmpty();
    }

}
