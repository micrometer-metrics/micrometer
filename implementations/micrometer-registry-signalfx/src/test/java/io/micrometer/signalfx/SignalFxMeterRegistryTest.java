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
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.step.PollingAwareMockStepClock;
import io.micrometer.core.instrument.util.DoubleFormat;
import org.assertj.core.api.Condition;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.CUMULATIVE_COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.GAUGE;
import static io.micrometer.core.instrument.util.TimeUtils.millisToUnit;
import static org.assertj.core.api.Assertions.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;

/**
 * Tests for {@link SignalFxMeterRegistry}.
 *
 * @author Johnny Lim
 */
@SuppressWarnings("deprecation")
class SignalFxMeterRegistryTest {

    private final SignalFxConfig config = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
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

    private final SignalFxConfig cumulativeHistogramConfig = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public boolean publishCumulativeHistogram() {
            return true;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    private final SignalFxConfig cumulativeDeltaConfig = new SignalFxConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public boolean publishDeltaHistogram() {
            return true;
        }

        @Override
        public boolean publishCumulativeHistogram() {
            return true;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }
    };

    @Test
    void shouldConfigureCumulativeHistogram_Timer() {
        MockClock clock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeHistogramConfig, clock);
        Timer timer = Timer.builder("testTimer")
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(100), Duration.ofMillis(1000))
            .distributionStatisticExpiry(Duration.ofSeconds(10))
            .distributionStatisticBufferLength(1)
            .register(registry);

        timer.record(Duration.ofSeconds(10));
        clock.add(Duration.ofSeconds(3));
        timer.record(Duration.ofMillis(5));
        clock.add(Duration.ofSeconds(3));
        timer.record(Duration.ofMillis(50));
        clock.add(Duration.ofSeconds(3));
        timer.record(Duration.ofMillis(500));
        clock.add(Duration.ofSeconds(3));
        timer.record(Duration.ofSeconds(5));

        // max moves over the first observed 10s value
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(5);
        // histogram counts reflect everything that was observed
        assertThat(timer.takeSnapshot().histogramCounts()).containsExactly(
                new CountAtBucket(millisToUnit(10, TimeUnit.NANOSECONDS), 1),
                new CountAtBucket(millisToUnit(100, TimeUnit.NANOSECONDS), 2),
                new CountAtBucket(millisToUnit(1000, TimeUnit.NANOSECONDS), 3), new CountAtBucket(Double.MAX_VALUE, 5));
    }

    @Test
    void shouldConfigureCumulativeHistogram_DistributionSummary() {
        MockClock clock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeHistogramConfig, clock);
        DistributionSummary summary = DistributionSummary.builder("testSummary")
            .serviceLevelObjectives(10, 100, 1000)
            .distributionStatisticExpiry(Duration.ofSeconds(10))
            .distributionStatisticBufferLength(1)
            .register(registry);

        summary.record(10_000);
        clock.add(Duration.ofSeconds(3));
        summary.record(5);
        clock.add(Duration.ofSeconds(3));
        summary.record(50);
        clock.add(Duration.ofSeconds(3));
        summary.record(500);
        clock.add(Duration.ofSeconds(3));
        summary.record(5_000);

        // max moves over the first observed 10_000 value
        assertThat(summary.max()).isEqualTo(5_000);
        // histogram counts reflect everything that was observed
        assertThat(summary.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(10d, 1),
                new CountAtBucket(100d, 2), new CountAtBucket(1000d, 3), new CountAtBucket(Double.MAX_VALUE, 5));
    }

    @ParameterizedTest
    @ArgumentsSource(TimerArguments.class)
    void shouldExportCumulativeHistogramData_Timer(Duration[] buckets, double[] percentiles) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeHistogramConfig, mockClock);
        Timer timer = Timer.builder("my.timer")
            .publishPercentiles(percentiles)
            .serviceLevelObjectives(buckets)
            .register(registry);

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step());

        List<SignalFxProtocolBuffers.DataPoint> dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.timer.avg", 0.55), atIndex(0))
            .has(counterPoint("my.timer.count", 4), atIndex(1))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.timer.max", 2), atIndex(7))
            .has(counterPoint("my.timer.totalTime", 2.2), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.timer.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.timer.percentile"), percentile("0.99")), atIndex(9));
        }

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step());

        dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.timer.avg", 0.55), atIndex(0))
            .has(counterPoint("my.timer.count", 4), atIndex(1))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 8), bucket("+Inf")), atIndex(2))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 2), bucket(buckets[1])), atIndex(4))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 4), bucket(buckets[2])), atIndex(5))
            .has(allOf(cumulativeCounterPoint("my.timer.histogram", 6), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.timer.max", 2), atIndex(7))
            .has(counterPoint("my.timer.totalTime", 2.2), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.timer.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.timer.percentile"), percentile("0.99")), atIndex(9));
        }

        registry.close();
    }

    @ParameterizedTest
    @ArgumentsSource(TimerArguments.class)
    void shouldExportDeltaHistogramData_Timer(Duration[] buckets, double[] percentiles) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeDeltaConfig, mockClock);
        Timer timer = Timer.builder("my.timer")
            .publishPercentiles(percentiles)
            .serviceLevelObjectives(buckets)
            .register(registry);

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step());

        List<SignalFxProtocolBuffers.DataPoint> dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.timer.avg", 0.55), atIndex(0))
            .has(counterPoint("my.timer.count", 4), atIndex(1))
            .has(allOf(counterPoint("my.timer.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.timer.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.timer.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.timer.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(counterPoint("my.timer.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.timer.max", 2), atIndex(7))
            .has(counterPoint("my.timer.totalTime", 2.2), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.timer.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.timer.percentile"), percentile("0.99")), atIndex(9));
        }

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.SECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step());

        dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.timer.avg", 0.55), atIndex(0))
            .has(counterPoint("my.timer.count", 4), atIndex(1))
            .has(allOf(counterPoint("my.timer.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.timer.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.timer.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.timer.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(counterPoint("my.timer.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.timer.max", 2), atIndex(7))
            .has(counterPoint("my.timer.totalTime", 2.2), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.timer.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.timer.percentile"), percentile("0.99")), atIndex(9));
        }

        registry.close();
    }

    static final class TimerArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            Object emptyPercentile = new double[0];
            Object percentile = new double[] { 0.95, 0.99 };
            Object buckets = Arrays.array(Duration.ofMillis(1), Duration.ofMillis(10), Duration.ofMillis(100),
                    Duration.ofMillis(1000));
            Object bucketsWithInf = Arrays.array(Duration.ofMillis(1), Duration.ofMillis(10), Duration.ofMillis(100),
                    Duration.ofMillis(1000), Duration.ofNanos(Long.MAX_VALUE));
            return Stream.of(Arguments.of(buckets, emptyPercentile), Arguments.of(bucketsWithInf, emptyPercentile),
                    Arguments.of(buckets, percentile), Arguments.of(bucketsWithInf, percentile));
        }

    }

    @ParameterizedTest
    @ArgumentsSource(DistributionSummaryArguments.class)
    void shouldExportCumulativeHistogramData_DistributionSummary(double[] buckets, double[] percentiles) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeHistogramConfig, mockClock);
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution")
            .publishPercentiles(percentiles)
            .serviceLevelObjectives(buckets)
            .register(registry);

        distributionSummary.record(5);
        distributionSummary.record(20);
        distributionSummary.record(175);
        distributionSummary.record(2000);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(cumulativeHistogramConfig.step());

        List<SignalFxProtocolBuffers.DataPoint> dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.distribution.avg", 550), atIndex(0))
            .has(counterPoint("my.distribution.count", 4), atIndex(1))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.distribution.max", 2000), atIndex(7))
            .has(counterPoint("my.distribution.totalTime", 2200), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.99")), atIndex(9));
        }

        distributionSummary.record(5);
        distributionSummary.record(20);
        distributionSummary.record(175);
        distributionSummary.record(2000);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(cumulativeHistogramConfig.step());

        dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.distribution.avg", 550), atIndex(0))
            .has(counterPoint("my.distribution.count", 4), atIndex(1))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 8), bucket("+Inf")), atIndex(2))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 2), bucket(buckets[1])), atIndex(4))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 4), bucket(buckets[2])), atIndex(5))
            .has(allOf(cumulativeCounterPoint("my.distribution.histogram", 6), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.distribution.max", 2000), atIndex(7))
            .has(counterPoint("my.distribution.totalTime", 2200), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.99")), atIndex(9));
        }

        registry.close();
    }

    @ParameterizedTest
    @ArgumentsSource(DistributionSummaryArguments.class)
    void shouldExportDeltaHistogramData_DistributionSummary(double[] buckets, double[] percentiles) {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeDeltaConfig, mockClock);
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution")
            .publishPercentiles(percentiles)
            .serviceLevelObjectives(buckets)
            .register(registry);

        distributionSummary.record(5);
        distributionSummary.record(20);
        distributionSummary.record(175);
        distributionSummary.record(2000);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(cumulativeHistogramConfig.step());

        List<SignalFxProtocolBuffers.DataPoint> dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.distribution.avg", 550), atIndex(0))
            .has(counterPoint("my.distribution.count", 4), atIndex(1))
            .has(allOf(counterPoint("my.distribution.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.distribution.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.distribution.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.distribution.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(counterPoint("my.distribution.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.distribution.max", 2000), atIndex(7))
            .has(counterPoint("my.distribution.totalTime", 2200), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.99")), atIndex(9));
        }

        distributionSummary.record(5);
        distributionSummary.record(20);
        distributionSummary.record(175);
        distributionSummary.record(2000);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(cumulativeHistogramConfig.step());

        dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(9 + percentiles.length)
            .has(gaugePoint("my.distribution.avg", 550), atIndex(0))
            .has(counterPoint("my.distribution.count", 4), atIndex(1))
            .has(allOf(counterPoint("my.distribution.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.distribution.histogram", 0), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.distribution.histogram", 1), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.distribution.histogram", 2), bucket(buckets[2])), atIndex(5))
            .has(allOf(counterPoint("my.distribution.histogram", 3), bucket(buckets[3])), atIndex(6))
            .has(gaugePoint("my.distribution.max", 2000), atIndex(7))
            .has(counterPoint("my.distribution.totalTime", 2200), atIndex(8 + percentiles.length));
        if (percentiles.length > 0) {
            assertThat(dataPoints).has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.95")), atIndex(8))
                .has(allOf(gaugePoint("my.distribution.percentile"), percentile("0.99")), atIndex(9));
        }

        registry.close();
    }

    static final class DistributionSummaryArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            Object emptyPercentile = new double[0];
            Object percentile = new double[] { 0.95, 0.99 };
            Object buckets = new double[] { 1.0, 10, 100, 1000 };
            Object bucketsWithInf = new double[] { 1.0, 10, 100, 1000, Double.MAX_VALUE };
            return Stream.of(Arguments.of(buckets, emptyPercentile), Arguments.of(bucketsWithInf, emptyPercentile),
                    Arguments.of(buckets, percentile), Arguments.of(bucketsWithInf, percentile));
        }

    }

    @Test
    void shouldNotExportCumulativeHistogramDataByDefault_Timer() {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(config, mockClock);
        Timer timer = Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(10), Duration.ofMillis(100),
                    Duration.ofMillis(1000))
            .distributionStatisticExpiry(Duration.ofSeconds(10))
            .distributionStatisticBufferLength(1)
            .register(registry);

        timer.record(50, TimeUnit.MILLISECONDS);
        timer.record(5000, TimeUnit.MILLISECONDS);
        mockClock.add(config.step());
        getDataPoints(registry, mockClock.wallTime());

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(500, TimeUnit.MILLISECONDS);
        mockClock.add(config.step().minus(Duration.ofMillis(1)));

        assertThat(getDataPoints(registry, mockClock.wallTime())).hasSize(8)
            .has(gaugePoint("my.timer.avg", 0.2525), atIndex(0))
            .has(counterPoint("my.timer.count", 2), atIndex(1))
            .has(allOf(gaugePoint("my.timer.histogram", 0), bucket(Duration.ofMillis(1))), atIndex(2))
            .has(allOf(gaugePoint("my.timer.histogram", 1), bucket(Duration.ofMillis(10))), atIndex(3))
            .has(allOf(gaugePoint("my.timer.histogram", 1), bucket(Duration.ofMillis(100))), atIndex(4))
            .has(allOf(gaugePoint("my.timer.histogram", 2), bucket(Duration.ofMillis(1000))), atIndex(5))
            .has(gaugePoint("my.timer.max", 0.5), atIndex(6))
            .has(counterPoint("my.timer.totalTime", 0.505), atIndex(7));

        registry.close();
    }

    @Test
    void shouldNotExportCumulativeHistogramDataByDefault_DistributionSummary() {
        MockClock mockClock = new MockClock();
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(config, mockClock);
        DistributionSummary summary = DistributionSummary.builder("my.distribution")
            .serviceLevelObjectives(1, 10, 100, 1000)
            .distributionStatisticExpiry(Duration.ofSeconds(10))
            .distributionStatisticBufferLength(1)
            .register(registry);

        summary.record(50);
        summary.record(5000);
        mockClock.add(config.step());
        getDataPoints(registry, mockClock.wallTime());

        summary.record(5);
        summary.record(500);
        mockClock.add(config.step().minus(Duration.ofMillis(1)));

        assertThat(getDataPoints(registry, mockClock.wallTime())).hasSize(8)
            .has(gaugePoint("my.distribution.avg", 252.5), atIndex(0))
            .has(counterPoint("my.distribution.count", 2), atIndex(1))
            .has(allOf(gaugePoint("my.distribution.histogram", 0), bucket(1)), atIndex(2))
            .has(allOf(gaugePoint("my.distribution.histogram", 1), bucket(10)), atIndex(3))
            .has(allOf(gaugePoint("my.distribution.histogram", 1), bucket(100)), atIndex(4))
            .has(allOf(gaugePoint("my.distribution.histogram", 2), bucket(1000)), atIndex(5))
            .has(gaugePoint("my.distribution.max", 500), atIndex(6))
            .has(counterPoint("my.distribution.totalTime", 505), atIndex(7));

        registry.close();
    }

    @Test
    @Issue("3774")
    void deltaHistogramCountsShouldMatchWithTimerCount() {
        PollingAwareMockStepClock mockClock = new PollingAwareMockStepClock(cumulativeDeltaConfig);
        SignalFxMeterRegistry registry = new SignalFxMeterRegistry(cumulativeDeltaConfig, mockClock);
        Duration[] buckets = new Duration[] { Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100) };
        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(buckets).register(registry);

        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(175, TimeUnit.MILLISECONDS);
        timer.record(2, TimeUnit.MILLISECONDS);

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step(), registry);
        mockClock.add(config.step().dividedBy(2), registry);
        // Recording for next step has started. But these values should be reported only
        // in then next step.
        timer.record(5, TimeUnit.MILLISECONDS);
        timer.record(25, TimeUnit.MILLISECONDS);
        timer.record(75, TimeUnit.MILLISECONDS);
        timer.record(125, TimeUnit.MILLISECONDS);
        timer.record(500, TimeUnit.MILLISECONDS);

        // Current is not elapsed, so only the values recorded before start of this step
        // should be reflected yet.
        // Assert that data recorded for previous step is available.
        List<SignalFxProtocolBuffers.DataPoint> dataPoints = getDataPoints(registry, mockClock.wallTime());
        assertThat(dataPoints).hasSize(8)
            .has(gaugePoint("my.timer.avg", 0.0505), atIndex(0))
            .has(counterPoint("my.timer.count", 4), atIndex(1))
            .has(allOf(counterPoint("my.timer.histogram", 4), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.timer.histogram", 2), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.timer.histogram", 3), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.timer.histogram", 3), bucket(buckets[2])), atIndex(5))
            .has(gaugePoint("my.timer.max", 0.5), atIndex(6))
            .has(counterPoint("my.timer.totalTime", 0.202), atIndex(7));

        // Advance time, so we are in the "next" step where currently recorded values will
        // be reported.
        mockClock.add(config.step(), registry);

        dataPoints = getDataPoints(registry, mockClock.wallTime());
        // Assert that data recorded for previous step is available.
        assertThat(dataPoints).hasSize(8)
            .has(gaugePoint("my.timer.avg", 0.146), atIndex(0))
            .has(counterPoint("my.timer.count", 5), atIndex(1))
            .has(allOf(counterPoint("my.timer.histogram", 5), bucket("+Inf")), atIndex(2))
            .has(allOf(counterPoint("my.timer.histogram", 1), bucket(buckets[0])), atIndex(3))
            .has(allOf(counterPoint("my.timer.histogram", 2), bucket(buckets[1])), atIndex(4))
            .has(allOf(counterPoint("my.timer.histogram", 3), bucket(buckets[2])), atIndex(5))
            .has(gaugePoint("my.timer.max", 0.5), atIndex(6))
            .has(counterPoint("my.timer.totalTime", 0.73), atIndex(7));

        registry.close();
    }

    private static List<SignalFxProtocolBuffers.DataPoint> getDataPoints(SignalFxMeterRegistry registry,
            long timestamp) {
        return registry.getMeters()
            .stream()
            .map(meter -> meter.match(registry::addGauge, registry::addCounter, registry::addTimer,
                    registry::addDistributionSummary, registry::addLongTaskTimer, registry::addTimeGauge,
                    registry::addFunctionCounter, registry::addFunctionTimer, registry::addMeter))
            .flatMap(builders -> builders.map(builder -> builder.setTimestamp(timestamp).build()))
            .sorted(Comparator.comparing(SignalFxProtocolBuffers.DataPoint::getMetric)
                .thenComparing((point) -> point.getDimensions(0).getValue()))
            .sorted((dt1, dt2) -> {
                int ret1 = dt1.getMetric().compareTo(dt2.getMetric());
                if (ret1 == 0) {
                    if (dt1.getDimensionsCount() > 0 && dt2.getDimensionsCount() > 0) {
                        return dt1.getDimensions(0).getValue().compareTo(dt2.getDimensions(0).getValue());
                    }
                }
                return ret1;
            })
            .collect(Collectors.toList());
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> bucket(double bucket) {
        return bucket(DoubleFormat.wholeOrDecimal(bucket));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> bucket(Duration bucket) {
        return bucket(DoubleFormat.wholeOrDecimal(bucket.toMillis() / 1000.0));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> bucket(String bucketStr) {
        return allOf(new Condition<>(point -> point.getDimensions(0).getKey().equals("le"), "Has 'le' dimension"),
                new Condition<>(point -> point.getDimensions(0).getValue().equals(bucketStr),
                        "Has 'le' dimension with value %s", bucketStr));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> percentile(String percentileStr) {
        return allOf(new Condition<>(point -> point.getDimensions(0).getKey().equals("phi"), "Has 'phi' dimension"),
                new Condition<>(point -> point.getDimensions(0).getValue().equals(percentileStr),
                        "Has 'phi' dimension with value %s", percentileStr));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> counterPoint(String name, double value) {
        return allOf(new Condition<>(point -> point.getMetric().equals(name), "Has name %s", name),
                new Condition<>(point -> point.getMetricType().equals(COUNTER), "Has COUNTER type"), hasValue(value));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> cumulativeCounterPoint(String name, double value) {
        return allOf(new Condition<>(point -> point.getMetric().equals(name), "Has name %s", name),
                new Condition<>(point -> point.getMetricType().equals(CUMULATIVE_COUNTER),
                        "Has CUMULATIVE_COUNTER type"),
                hasValue(value));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> gaugePoint(String name, double value) {
        return allOf(gaugePoint(name), hasValue(value));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> gaugePoint(String name) {
        return allOf(new Condition<>(point -> point.getMetric().equals(name), "Has name %s", name),
                new Condition<>(point -> point.getMetricType().equals(GAUGE), "Has GAUGE type"));
    }

    private static Condition<SignalFxProtocolBuffers.DataPoint> hasValue(double value) {
        return new Condition<>(point -> {
            SignalFxProtocolBuffers.Datum v = point.getValue();
            if (v.hasDoubleValue()) {
                return v.getDoubleValue() == value;
            }
            if (v.hasIntValue()) {
                return v.getIntValue() == (int) value;
            }
            return false;
        }, "Has value %s", value);
    }

}
