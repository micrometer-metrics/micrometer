/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.bigquery;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BigQueryMeterRegistry}.
 *
 * @author Lui Baeumer
 */
class BigQueryMeterRegistryTest {

    private final BigQueryConfig config = new BigQueryConfig() {
        public String project() {
            return "myprj";
        }

        public String get(String key) {
            return null;
        }
    };

    private final MockClock clock = new MockClock();
    private final BigQueryMeterRegistry meterRegistry
            = BigQueryMeterRegistry.builder(config)
            .clock(clock).build();

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.NaN)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.NaN)).isEmpty();
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeCounterWithFunction() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(meterRegistry.writeMeter(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);

        assertThat(meterRegistry
                .writeMeter(meter))
                .containsOnly(Map.of("_measurement", "my_meter",
                        "metric_type", "gauge",
                        "_time", "1970-01-01 01:00:00.001",
                        "value", 1d));
    }

    @Test
    void nanFunctionTimerShouldNotBeWritten() {
        FunctionTimer timer = FunctionTimer.builder("myFunctionTimer", Double.NaN, Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionTimer(timer)).isEmpty();
    }

    @Test
    void nanMeanFunctionTimerShouldNotWriteMean() {
        FunctionTimer functionTimer = new FunctionTimer() {
            @Override
            public double count() {
                return 1;
            }

            @Override
            public double totalTime(TimeUnit unit) {
                return 1;
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public Id getId() {
                return new Id("func.timer", Tags.empty(), null, null, Type.TIMER);
            }

            @Override
            public double mean(TimeUnit unit) {
                return Double.NaN;
            }
        };

        assertThat(meterRegistry.writeFunctionTimer(functionTimer))
                .containsOnly(Map.of("_measurement", "func_timer",
                        "metric_type", "histogram",
                        "count", 1d,
                        "sum", 1d,
                        "_time", "1970-01-01 01:00:00.001"
                ));
    }
}
