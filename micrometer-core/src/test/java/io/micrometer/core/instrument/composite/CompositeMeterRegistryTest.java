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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Jon Schneider
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

        Counter.builder("counter").baseUnit("bytes").register(composite);
        DistributionSummary.builder("summary").baseUnit("bytes").register(composite);
        Gauge.builder("gauge", new AtomicInteger(0), AtomicInteger::get).baseUnit("bytes").register(composite);

        assertThat(simple.get("counter").counter().getId().getBaseUnit()).isEqualTo("bytes");
        assertThat(simple.get("summary").summary().getId().getBaseUnit()).isEqualTo("bytes");
        assertThat(simple.get("gauge").gauge().getId().getBaseUnit()).isEqualTo("bytes");
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

        // simple counter doesn't receive the increment after simple is removed from the composite
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
        simple.config().commonTags("instance", "local"); // added alongside other common tags in the composite
        simple.config().commonTags("region", "us-west-1"); // overriden by the composite

        composite.config().commonTags("region", "us-east-1");
        composite.add(simple);
        composite.config().commonTags("stack", "test");

        composite.counter("counter").increment();

        simple.get("counter").tags("region", "us-east-1", "stack", "test",
                "instance", "local").counter();
    }

    @DisplayName("function timer base units are delegated to registries in the composite")
    @Test
    void functionTimerUnits() {
        composite.add(simple);
        Object o = new Object();

        composite.more().timer("function.timer", emptyList(),
                o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer functionTimer = simple.get("function.timer").functionTimer();
        assertThat(functionTimer.count()).isEqualTo(1);
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1);
    }

    @Issue("#255")
    @Test
    void castingFunctionCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();

        FunctionCounter.builder("foo", 1L, x -> x)
                .register(compositeMeterRegistry);

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

        // if the meter is registered directly to the child, the composite config does not take effect
        simple.counter("deny.composite");
        simple.get("deny.composite").meter();
    }

    @Test
    void histogramConfigDefaultIsNotAffectedByComposite() {
        composite.add(simple);

        // the expiry on this timer is determined by the simple registry's default histogram config
        Timer t = Timer.builder("my.timer")
                .distributionStatisticBufferLength(1)
                .register(composite);

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
        Flux.range(0, 10000)
                .parallel(8)
                .doOnTerminate(latch::countDown)
                .runOn(Schedulers.parallel())
                .subscribe(n -> {
                    if (n % 2 == 0)
                        composite2.add(simple);
                    else composite2.remove(simple);
                });

        latch.await(10, TimeUnit.SECONDS);
        assertThat(latch.getCount()).isZero();
    }
}
