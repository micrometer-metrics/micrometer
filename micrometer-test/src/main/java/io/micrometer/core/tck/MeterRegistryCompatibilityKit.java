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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.TimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.MockClock.clock;
import static io.micrometer.core.instrument.Statistic.Count;
import static io.micrometer.core.instrument.Statistic.Total;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
@ExtendWith(RegistryResolver.class)
public abstract class MeterRegistryCompatibilityKit {
    public abstract MeterRegistry registry();

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

        assertThat(registry.find("foo").meters().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("find meters by name and class type matching a subset of their tags")
    void findMeters(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c1);
        assertThat(registry.find("bar").tags("k", "v").counter()).containsSame(c2);
    }

    @Test
    @DisplayName("find meters by name and type matching a subset of their tags")
    void findMetersByType(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c1);
        assertThat(registry.find("bar").tags("k", "v").counter()).containsSame(c2);
    }

    @Test
    @DisplayName("find meters by name and value")
    void findMetersByValue(MeterRegistry registry) {
        Counter c = registry.counter("counter");
        c.increment();

        Timer t = registry.timer("timer");
        t.record(10, TimeUnit.NANOSECONDS);

        clock(registry).addAndGet(1, TimeUnit.SECONDS);

        assertThat(registry.find("counter").value(Count, 1.0).counter()).isPresent();
        assertThat(registry.find("timer").value(Count, 1.0).timer()).isPresent();
        assertThat(registry.find("timer").value(Total, 10.0).timer()).isPresent();
    }

    @Test
    @DisplayName("common tags are added to every measurement")
    void addCommonTags(MeterRegistry registry) {
        registry.config().commonTags("k", "v");
        Counter c = registry.counter("foo");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c);
        assertThat(c.getId().getTags()).hasSize(1);
    }

    @Test
    @DisplayName("original and convention names are preserved for custom meter types")
    void aTaleOfTwoNames(MeterRegistry registry) {
        AtomicInteger n = new AtomicInteger(1);
        registry.more().counter(registry.createId("my.counter", Collections.emptyList(), null), n);

        assertThat(registry.find("my.counter").meter()).isPresent();
    }

    @Test
    @DisplayName("function timers respect the base unit of an underlying registry")
    void functionTimerUnits(MeterRegistry registry) {
        Object o = new Object();

        registry.more().timer(registry.createId("function.timer", emptyList(), "test"),
            o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        Optional<Meter> meter = registry.find("function.timer").meter();
        assertThat(meter).isPresent();

        Iterable<Measurement> measurements = meter.get().measure();
        assertThat(measurements)
            .anySatisfy(ms -> {
                TimeUnit baseUnit = TimeUnit.valueOf(meter.get().getId().getBaseUnit().toUpperCase());
                assertThat(ms.getStatistic()).isEqualTo(Statistic.Total);
                assertThat(TimeUtils.convert(ms.getValue(), baseUnit, TimeUnit.MILLISECONDS)).isEqualTo(1);
            });
    }

    @DisplayName("counters")
    @Nested
    class CounterTck implements CounterTest {}

    @DisplayName("distribution summaries")
    @Nested
    class DistributionSummaryTck implements DistributionSummaryTest {}

    @DisplayName("gauges")
    @Nested
    class GaugeTck implements GaugeTest {}

    @DisplayName("long task timers")
    @Nested
    class LongTaskTimerTck implements LongTaskTimerTest {}

    @DisplayName("timers")
    @Nested
    class TimerTck implements TimerTest {}
}

