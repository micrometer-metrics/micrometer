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
package io.micrometer.opentsdb;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTSDBMeterRegistryTest {

    private final OpenTSDBConfig config = OpenTSDBConfig.DEFAULT;

    private final MockClock clock = new MockClock();

    private final OpenTSDBMeterRegistry meterRegistry = new OpenTSDBMeterRegistry(config, clock);

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).hasSize(1);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).hasSize(1);
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("my.counter", 1d, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).hasSize(1);
    }

    @Test
    void writeFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("my.counter", Double.POSITIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("my.counter", Double.NEGATIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();
    }

    @Issue("#2060")
    @Test
    void histogramBucketsHaveCorrectBaseUnit() {
        Timer timer = Timer.builder("my.timer")
            .publishPercentileHistogram()
            .serviceLevelObjectives(Duration.ofMillis(900), Duration.ofSeconds(1))
            .register(meterRegistry);

        timer.record(1, TimeUnit.SECONDS);
        clock.add(config.step());

        assertThat(meterRegistry.writeTimer(timer)).contains(
                "{\"metric\":\"my_timer_duration_seconds_bucket\",\"timestamp\":60001,\"value\":1,\"tags\":{\"le\":\"1.0\"}}")
            .contains(
                    "{\"metric\":\"my_timer_duration_seconds_bucket\",\"timestamp\":60001,\"value\":0,\"tags\":{\"le\":\"0.9\"}}");
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("my.timer").tag("tag", "value").register(meterRegistry);
        meterRegistry.writeLongTaskTimer(timer).forEach(System.out::println);

        assertThat(meterRegistry.writeLongTaskTimer(timer)).contains(
                "{\"metric\":\"my_timer_duration_seconds_active_count\",\"timestamp\":1,\"value\":0,\"tags\":{\"tag\":\"value\"}}")
            .contains(
                    "{\"metric\":\"my_timer_duration_seconds_duration_sum\",\"timestamp\":1,\"value\":0,\"tags\":{\"tag\":\"value\"}}")
            .contains(
                    "{\"metric\":\"my_timer_duration_seconds_max\",\"timestamp\":1,\"value\":0,\"tags\":{\"tag\":\"value\"}}");
    }

}
