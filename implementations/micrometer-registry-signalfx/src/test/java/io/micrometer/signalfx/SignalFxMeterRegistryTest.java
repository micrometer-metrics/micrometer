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
package io.micrometer.signalfx;

import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.util.DoubleFormat;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SignalFxMeterRegistry}.
 *
 * @author Johnny Lim
 */
class SignalFxMeterRegistryTest {

    private final SignalFxConfig config = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    private final SignalFxConfig configRecording = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public Boolean reportHistogramBuckets() {
            return true;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    @Test
    void addHistogramSnapshot_WithNoRecording() {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(this.config, mockClock);
        Timer timer =
                Timer.builder("my.timer").serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(10),
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(5000),
                        Duration.ofMillis(10000)).register(registry);
        timer.record(Duration.ofMillis(25));
        timer.record(Duration.ofMillis(50));
        timer.record(Duration.ofMillis(75));
        timer.record(Duration.ofMillis(250));
        timer.record(Duration.ofMillis(600));

        // Advance time, so we are in the "next" step where currently recorded values will be
        // reported.
        mockClock.add(Duration.ofMillis(10000));
        List<SignalFxProtocolBuffers.DataPoint> histogramPoints =
                registry.addHistogramSnapshot(timer.getId(), timer.takeSnapshot())
                        .map(SignalFxProtocolBuffers.DataPoint.Builder::build)
                        .collect(Collectors.toList());
        assertThat(histogramPoints).hasSize(4);
        assertThat(histogramPoints.get(0).getMetric()).isEqualTo("my.timer.count");
        assertThat(histogramPoints.get(0).getValue().getIntValue()).isEqualTo(5);
        assertThat(histogramPoints.get(1).getMetric()).isEqualTo("my.timer.totalTime");
        assertThat(histogramPoints.get(1).getValue().getDoubleValue()).isEqualTo(1);
        assertThat(histogramPoints.get(2).getMetric()).isEqualTo("my.timer.avg");
        assertThat(histogramPoints.get(2).getValue().getDoubleValue()).isEqualTo(0.2);
        assertThat(histogramPoints.get(3).getMetric()).isEqualTo("my.timer.max");
        assertThat(histogramPoints.get(3).getValue().getDoubleValue()).isEqualTo(0.6);

        registry.close();
    }

    @Test
    void addHistogramSnapshot_WithRecording() {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(this.configRecording, mockClock);
        Duration[] buckets = Arrays.array(Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofMillis(100),
                Duration.ofMillis(1000));
        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(buckets).register(registry);
        timer.record(25, TimeUnit.MILLISECONDS);
        timer.record(50, TimeUnit.MILLISECONDS);
        timer.record(75, TimeUnit.MILLISECONDS);
        timer.record(250, TimeUnit.MILLISECONDS);
        timer.record(600, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will be
        // reported.
        mockClock.add(Duration.ofMillis(10000));
        HistogramSnapshot snapshot = timer.takeSnapshot();
        List<SignalFxProtocolBuffers.DataPoint> histogramPoints =
                registry.addHistogramSnapshot(timer.getId(), snapshot)
                        .map(SignalFxProtocolBuffers.DataPoint.Builder::build)
                        .collect(Collectors.toList());
        assertThat(histogramPoints).hasSize(4 + buckets.length + 1 /* +Inf */);
        assertThat(histogramPoints.get(0).getMetric()).isEqualTo("my.timer.count");
        assertThat(histogramPoints.get(0).getValue().getIntValue()).isEqualTo(6);
        assertThat(histogramPoints.get(1).getMetric()).isEqualTo("my.timer.totalTime");
        assertThat(histogramPoints.get(1).getValue().getDoubleValue()).isEqualTo(3);
        assertThat(histogramPoints.get(2).getMetric()).isEqualTo("my.timer.avg");
        assertThat(histogramPoints.get(2).getValue().getDoubleValue()).isEqualTo(0.5);
        assertThat(histogramPoints.get(3).getMetric()).isEqualTo("my.timer.max");
        assertThat(histogramPoints.get(3).getValue().getDoubleValue()).isEqualTo(2);

        for (int i = 0; i < buckets.length; i++) {
            assertThat(histogramPoints.get(4 + i).getMetric()).isEqualTo("my.timer_bucket");
            assertThat(histogramPoints.get(4 + i).getDimensions(0).getKey()).isEqualTo(
                    "upper_bound");
            assertThat(histogramPoints.get(4 + i).getDimensions(0).getValue()).isEqualTo(
                    DoubleFormat.wholeOrDecimal(buckets[i].toMillis() / 1000.0));
            assertThat(histogramPoints.get(4 + i).getValue().getIntValue()).isEqualTo(0);
        }

        assertThat(histogramPoints.get(4 + buckets.length).getMetric()).isEqualTo("my.timer_bucket");
        assertThat(histogramPoints.get(4 + buckets.length).getDimensions(0).getKey()).isEqualTo(
                "upper_bound");
        assertThat(histogramPoints.get(4 + buckets.length).getDimensions(0).getValue()).isEqualTo("+Inf");
        assertThat(histogramPoints.get(4 + buckets.length).getValue().getDoubleValue()).isEqualTo(6);

        registry.close();
    }

}
