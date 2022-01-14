/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.signalfx;

import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.util.DoubleFormat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.CUMULATIVE_COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.GAUGE;
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
        public Duration step() {
            return Duration.ofSeconds(10);
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    private final SignalFxConfig configFixType = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Boolean fixHistogramBucketsType() {
            return true;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    @Test
    void newDistributionSummary_WithoutInf() {
        double[] buckets = new double[]{1.0, 10, 100, 1000};
        testDistributionSummary(buckets, config, GAUGE);
    }

    @Test
    void newDistributionSummary_WithInf() {
        double[] buckets = new double[]{1.0, 10, 100, 1000, Double.MAX_VALUE};
        testDistributionSummary(buckets, config, GAUGE);
    }

    @Test
    void newDistributionSummary_WithTypeFix() {
        double[] buckets = new double[]{1.0, 10, 100, 1000};
        testDistributionSummary(buckets, configFixType, CUMULATIVE_COUNTER);
    }

    @Test
    void newTimer_WithoutInf() {
        Duration[] buckets = Arrays.array(Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofMillis(100),
                Duration.ofMillis(1000));
        testTimer(buckets, config, GAUGE);
    }

    @Test
    void newTimer_WithInf() {
        Duration[] buckets = Arrays.array(Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofMillis(100),
                Duration.ofMillis(1000),
                Duration.ofNanos(Long.MAX_VALUE));
        testTimer(buckets, config, GAUGE);
    }

    @Test
    void newTimer_WithTypeFix() {
        Duration[] buckets = Arrays.array(Duration.ofMillis(1),
                Duration.ofMillis(10),
                Duration.ofMillis(100),
                Duration.ofMillis(1000));
        testTimer(buckets, configFixType, CUMULATIVE_COUNTER);
    }

    void testDistributionSummary(double[] buckets, SignalFxConfig config, SignalFxProtocolBuffers.MetricType bucketCountMetricType) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(config, mockClock);
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution")
                .serviceLevelObjectives(buckets)
                .register(registry);
        distributionSummary.record(5);
        distributionSummary.record(20);
        distributionSummary.record(175);
        distributionSummary.record(2000);


        // Advance time, so we are in the "next" step where currently recorded values will be
        // reported.
        mockClock.add(Duration.ofSeconds(11));
        List<SignalFxProtocolBuffers.DataPoint> histogramPoints =
                getPoints(registry, mockClock.wallTime());
        assertThat(histogramPoints).hasSize(9);
        histogramPoints.sort((dataPoint, t1) -> {
            int cmp = dataPoint.getMetric().compareTo(t1.getMetric());
            if (cmp == 0) {
                return dataPoint.getDimensions(0).getValue().compareTo(
                        t1.getDimensions(0).getValue());
            }
            return cmp;
        });

        assertThat(histogramPoints.get(0).getMetric()).isEqualTo("my.distribution.avg");
        assertThat(histogramPoints.get(0).getValue().getDoubleValue()).isEqualTo(550);

        assertThat(histogramPoints.get(1).getMetric()).isEqualTo("my.distribution.count");
        assertThat(histogramPoints.get(1).getValue().getIntValue()).isEqualTo(4);

        assertThat(histogramPoints.get(2).getMetric()).isEqualTo("my.distribution.histogram");
        assertThat(histogramPoints.get(2).getMetricType()).isEqualTo(bucketCountMetricType);
        assertThat(histogramPoints.get(2).getDimensions(0).getKey()).isEqualTo("le");
        assertThat(histogramPoints.get(2).getDimensions(0).getValue()).isEqualTo("+Inf");
        assertThat(histogramPoints.get(2).getValue().getDoubleValue()).isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            assertThat(histogramPoints.get(3 + i).getMetric()).isEqualTo("my.distribution.histogram");
            assertThat(histogramPoints.get(3 + i).getMetricType()).isEqualTo(bucketCountMetricType);
            assertThat(histogramPoints.get(3 + i).getDimensions(0).getKey()).isEqualTo("le");
            assertThat(histogramPoints.get(3 + i).getDimensions(0).getValue()).isEqualTo(
                    DoubleFormat.wholeOrDecimal(buckets[i]));
            assertThat(histogramPoints.get(3 + i).getValue().getDoubleValue()).isEqualTo(i);
        }

        assertThat(histogramPoints.get(7).getMetric()).isEqualTo("my.distribution.max");
        assertThat(histogramPoints.get(7).getValue().getDoubleValue()).isEqualTo(2000);

        assertThat(histogramPoints.get(8).getMetric()).isEqualTo("my.distribution.totalTime");
        assertThat(histogramPoints.get(8).getValue().getDoubleValue()).isEqualTo(2200);

        registry.close();
    }

    void testTimer(Duration[] buckets, SignalFxConfig config, SignalFxProtocolBuffers.MetricType bucketCountMetricType) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(config, mockClock);
        Timer timer = Timer.builder("my.timer")
                .serviceLevelObjectives(buckets)
                .register(registry);
        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);


        // Advance time, so we are in the "next" step where currently recorded values will be
        // reported.
        mockClock.add(Duration.ofSeconds(11));
        List<SignalFxProtocolBuffers.DataPoint> histogramPoints =
                getPoints(registry, mockClock.wallTime());
        assertThat(histogramPoints).hasSize(9);
        histogramPoints.sort((dataPoint, t1) -> {
            int cmp = dataPoint.getMetric().compareTo(t1.getMetric());
            if (cmp == 0) {
                return dataPoint.getDimensions(0).getValue().compareTo(
                        t1.getDimensions(0).getValue());
            }
            return cmp;
        });

        assertThat(histogramPoints.get(0).getMetric()).isEqualTo("my.timer.avg");
        assertThat(histogramPoints.get(0).getValue().getDoubleValue()).isEqualTo(0.55);

        assertThat(histogramPoints.get(1).getMetric()).isEqualTo("my.timer.count");
        assertThat(histogramPoints.get(1).getValue().getIntValue()).isEqualTo(4);

        assertThat(histogramPoints.get(2).getMetric()).isEqualTo("my.timer.histogram");
        assertThat(histogramPoints.get(2).getMetricType()).isEqualTo(bucketCountMetricType);
        assertThat(histogramPoints.get(2).getDimensions(0).getKey()).isEqualTo("le");
        assertThat(histogramPoints.get(2).getDimensions(0).getValue()).isEqualTo("+Inf");
        assertThat(histogramPoints.get(2).getValue().getDoubleValue()).isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            assertThat(histogramPoints.get(3 + i).getMetric()).isEqualTo("my.timer.histogram");
            assertThat(histogramPoints.get(3 + i).getMetricType()).isEqualTo(bucketCountMetricType);
            assertThat(histogramPoints.get(3 + i).getDimensions(0).getKey()).isEqualTo("le");
            assertThat(histogramPoints.get(3 + i).getDimensions(0).getValue()).isEqualTo(
                    DoubleFormat.wholeOrDecimal(buckets[i].toMillis() / 1000.0));
            assertThat(histogramPoints.get(3 + i).getValue().getDoubleValue()).isEqualTo(i);
        }

        assertThat(histogramPoints.get(7).getMetric()).isEqualTo("my.timer.max");
        assertThat(histogramPoints.get(7).getValue().getDoubleValue()).isEqualTo(2);

        assertThat(histogramPoints.get(8).getMetric()).isEqualTo("my.timer.totalTime");
        assertThat(histogramPoints.get(8).getValue().getDoubleValue()).isEqualTo(2.2);

        registry.close();
    }

    private List<SignalFxProtocolBuffers.DataPoint> getPoints(SignalFxMeterRegistry registry, long timestamp) {
        return registry.getMeters().stream().map(meter -> meter.match(
                registry::addGauge,
                registry::addCounter,
                registry::addTimer,
                registry::addDistributionSummary,
                registry::addLongTaskTimer,
                registry::addTimeGauge,
                registry::addFunctionCounter,
                registry::addFunctionTimer,
                registry::addMeter))
                .flatMap(builders -> builders.map(builder -> builder.setTimestamp(timestamp).build())).collect(Collectors.toList());
    }
}
