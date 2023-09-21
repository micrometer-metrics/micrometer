/*
 * Copyright 2023 VMware, Inc.
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Meter.Cache}.
 *
 * @author qweek
 */
public class MeterCacheTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void provideCounter() {
        Meter.Provider<String, Counter> counterProvider = Counter.builder("my.counter")
            .register(registry, value -> value == null ? Tags.empty() : Tags.of("my.key", value));

        assertThat(registry.getMeters()).hasSize(0);

        Counter firstCounter = counterProvider.get("first.value");
        firstCounter.increment();

        assertThat(registry.find("my.counter").counter()).isEqualTo(firstCounter);
        assertThat(firstCounter.count()).isEqualTo(1);

        Counter sameCounter = counterProvider.get("first.value");
        sameCounter.increment();

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(sameCounter.count()).isEqualTo(2);

        Counter secondCounter = counterProvider.get("second.value");
        secondCounter.increment();

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(secondCounter.count()).isEqualTo(1);
    }

    @Test
    void provideTimer() {
        Meter.Provider<String, Timer> timerProvider = Timer.builder("my.timer")
            .register(registry, value -> value == null ? Tags.empty() : Tags.of("my.key", value));

        assertThat(registry.getMeters()).hasSize(0);

        Timer firstTimer = timerProvider.get("first.value");
        firstTimer.record(1, TimeUnit.NANOSECONDS);

        assertThat(registry.find("my.timer").timer()).isEqualTo(firstTimer);
        assertThat(firstTimer.count()).isEqualTo(1);

        Timer sameTimer = timerProvider.get("first.value");
        sameTimer.record(2, TimeUnit.NANOSECONDS);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(sameTimer.count()).isEqualTo(2);

        Timer secondTimer = timerProvider.get("second.value");
        secondTimer.record(4, TimeUnit.NANOSECONDS);

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(secondTimer.count()).isEqualTo(1);
    }

    @Test
    void provideSummary() {
        Meter.Provider<String, DistributionSummary> summaryProvider = DistributionSummary.builder("my.summary")
            .register(registry, value -> value == null ? Tags.empty() : Tags.of("my.key", value));

        assertThat(registry.getMeters()).hasSize(0);

        DistributionSummary firstSummary = summaryProvider.get("first.value");
        firstSummary.record(1);

        assertThat(registry.find("my.summary").summary()).isEqualTo(firstSummary);
        assertThat(firstSummary.count()).isEqualTo(1);

        DistributionSummary sameSummary = summaryProvider.get("first.value");
        sameSummary.record(2);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(sameSummary.count()).isEqualTo(2);

        DistributionSummary secondSummary = summaryProvider.get("second.value");
        secondSummary.record(4);

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(secondSummary.count()).isEqualTo(1);
    }

    @Test
    void provideGauge() {
        Meter.Provider<String, AtomicInteger> gaugeProvider = Gauge
            .builder("my.gauge", null, AtomicInteger::doubleValue)
            .register(registry, AtomicInteger::new, value -> value == null ? Tags.empty() : Tags.of("my.key", value));

        assertThat(registry.getMeters()).hasSize(0);

        AtomicInteger firstGauge = gaugeProvider.get("first.value");
        firstGauge.set(1);

        assertThat(firstGauge.get()).isEqualTo(1);

        AtomicInteger sameGauge = gaugeProvider.get("first.value");

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(sameGauge).isEqualTo(firstGauge);

        AtomicInteger secondGauge = gaugeProvider.get("second.value");

        assertThat(registry.getMeters()).hasSize(2);
        assertThat(secondGauge).isNotEqualTo(firstGauge);
    }

}
