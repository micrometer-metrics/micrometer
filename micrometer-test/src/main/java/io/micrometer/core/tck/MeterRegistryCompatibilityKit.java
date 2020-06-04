/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for {@link MeterRegistry} compatibility tests.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@ExtendWith(RegistryResolver.class)
public abstract class MeterRegistryCompatibilityKit {

    // Retain this as a member field to prevent it to be garbage-collected in OpenJ9.
    private final Object o = new Object();

    public abstract MeterRegistry registry();
    public abstract Duration step();

    @Test
    @DisplayName("compatibility test provides a non-null registry instance")
    void registryIsNotNull(MeterRegistry registry) {
        assertThat(registry).isNotNull();
    }

    @Test
    @DisplayName("meters with the same name and tags are registered once")
    void uniqueMeters(MeterRegistry registry) {
        registry.counter("foo");
        registry.counter("foo");

        assertThat(registry.get("foo").meters().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("find meters by name and class type matching a subset of their tags")
    void findMeters(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c1);
        assertThat(registry.get("bar").tags("k", "v").counter()).isSameAs(c2);
    }

    @Test
    @DisplayName("find meters by name and type matching a subset of their tags")
    void findMetersByType(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c1);
        assertThat(registry.get("bar").tags("k", "v").counter()).isSameAs(c2);
    }

    @Test
    @DisplayName("find meters by name and value")
    void findMetersByValue(MeterRegistry registry) {
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
    void addCommonTags(MeterRegistry registry) {
        registry.config().commonTags("k", "v");
        Counter c = registry.counter("foo");

        assertThat(registry.get("foo").tags("k", "v").counter()).isSameAs(c);
        assertThat(c.getId().getTagsAsIterable()).hasSize(1);
    }

    @Test
    @DisplayName("original and convention names are preserved for custom meter types")
    void aTaleOfTwoNames(MeterRegistry registry) {
        AtomicInteger n = new AtomicInteger(1);
        registry.more().counter("my.counter", Collections.emptyList(), n);
        registry.get("my.counter").functionCounter();
    }

    @Test
    @DisplayName("function timers respect the base unit of an underlying registry")
    void functionTimerUnits(MeterRegistry registry) {
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
    void removeMeterWithSynthetic(MeterRegistry registry) {
        Timer timer = Timer.builder("my.timer")
                .publishPercentiles(0.95)
                .serviceLevelObjectives(Duration.ofMillis(10))
                .register(registry);

        registry.remove(timer);
    }

    @DisplayName("counters")
    @Nested
    class CounterTck implements CounterTest {
        @Override
        public Duration step() {
            return MeterRegistryCompatibilityKit.this.step();
        }
    }

    @DisplayName("distribution summaries")
    @Nested
    class DistributionSummaryTck implements DistributionSummaryTest {
        @Override
        public Duration step() {
            return MeterRegistryCompatibilityKit.this.step();
        }
    }

    @DisplayName("gauges")
    @Nested
    class GaugeTck implements GaugeTest {
    }

    @DisplayName("long task timers")
    @Nested
    class LongTaskTimerTck implements LongTaskTimerTest {
    }

    @DisplayName("timers")
    @Nested
    class TimerTck implements TimerTest {
        @Override
        public Duration step() {
            return MeterRegistryCompatibilityKit.this.step();
        }
    }
}

