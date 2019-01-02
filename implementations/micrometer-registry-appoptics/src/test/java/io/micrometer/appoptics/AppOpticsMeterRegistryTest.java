/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AppOpticsMeterRegistry}.
 *
 * @author Johnny Lim
 */
class AppOpticsMeterRegistryTest {

    private final AppOpticsMeterRegistry meterRegistry = new AppOpticsMeterRegistry(key -> null, Clock.SYSTEM);

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isTrue();
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isTrue();
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();
    }

}
