/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.Meter.Id;
import static io.micrometer.core.instrument.Meter.Type.DISTRIBUTION_SUMMARY;
import static io.micrometer.core.instrument.Meter.Type.TIMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CloudWatchMeterRegistry} with high resolution metrics and histogram.
 *
 * @author Christoph Sitter
 */
class CloudWatchMeterRegistryHighResolutionTest {
    private static final String METER_NAME = "test";
    private final CloudWatchConfig config = new CloudWatchConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String namespace() {
            return "namespace";
        }

        @Override
        public boolean useLegacyPublish() {
            return false;
        }

        @Override
        public boolean highResolution() {
            return true;
        }
    };

    private final MockClock clock = new MockClock();
    private final CloudWatchMeterRegistry registry = spy(new CloudWatchMeterRegistry(config, clock, null));
    private CloudWatchMeterRegistry.Batch registryBatch = registry.new Batch();


    @Test
    void shouldSerialiseTimerHistogramIntoMetricDatum() {
        Timer timer = mock(CloudWatchTimer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        final int histogramSamples = 50;
        final HistogramSnapshot histogram = buildHistogram(histogramSamples);
        when(timer.takeSnapshot()).thenReturn(histogram);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.timerData(timer);

        final List<MetricDatum> metricPuts = streamSupplier.get().collect(Collectors.toList());
        assertThat(metricPuts).hasSize(1);
        validateMetricPut(metricPuts.get(0), histogram, histogramSamples);
    }

    @Test
    void shouldSerialiseTimerHistogramIntoMultipleMetricDatums() {
        Timer timer = mock(CloudWatchTimer.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, TIMER);
        when(timer.getId()).thenReturn(meterId);
        final int histogramSamples = 350;
        final HistogramSnapshot histogram = buildHistogram(histogramSamples);
        when(timer.takeSnapshot()).thenReturn(histogram);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.timerData(timer);

        final List<MetricDatum> metricPuts = streamSupplier.get().collect(Collectors.toList());
        assertThat(metricPuts).hasSize(3);
    }

    @Test
    void shouldSerialiseSummaryHistogramIntoMetricDatum() {
        CloudWatchDistributionSummary summary = mock(CloudWatchDistributionSummary.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, DISTRIBUTION_SUMMARY);
        when(summary.getId()).thenReturn(meterId);
        final int histogramSamples = 50;
        final HistogramSnapshot histogram = buildHistogram(histogramSamples);
        when(summary.takeSnapshot()).thenReturn(histogram);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.summaryData(summary);

        final List<MetricDatum> metricPuts = streamSupplier.get().collect(Collectors.toList());
        assertThat(metricPuts).hasSize(1);
        validateMetricPut(metricPuts.get(0), histogram, histogramSamples);
    }

    @Test
    void shouldSerialiseSummaryHistogramIntoMultipleMetricDatums() {
        CloudWatchDistributionSummary summary = mock(CloudWatchDistributionSummary.class);
        Id meterId = new Id(METER_NAME, Tags.empty(), null, null, DISTRIBUTION_SUMMARY);
        when(summary.getId()).thenReturn(meterId);
        final int histogramSamples = 350;
        final HistogramSnapshot histogram = buildHistogram(histogramSamples);
        when(summary.takeSnapshot()).thenReturn(histogram);

        Supplier<Stream<MetricDatum>> streamSupplier = () -> registryBatch.summaryData(summary);

        final List<MetricDatum> metricPuts = streamSupplier.get().collect(Collectors.toList());
        assertThat(metricPuts).hasSize(3);
    }

    private void validateMetricPut(MetricDatum metricPuts, HistogramSnapshot histogram, int histogramSamples) {
        assertThat(metricPuts.storageResolution()).isEqualTo(1);
        assertThat(metricPuts.values()).hasSize(histogramSamples);
        assertThat(metricPuts.counts()).hasSize(histogramSamples);
        assertThat(metricPuts.statisticValues()).isNotNull();
        assertThat(metricPuts.statisticValues().maximum()).isEqualTo(histogram.max(TimeUnit.MILLISECONDS));
        assertThat(metricPuts.statisticValues().minimum()).isEqualTo(0);
        assertThat(metricPuts.statisticValues().sampleCount()).isEqualTo(histogram.count());
    }

    private HistogramSnapshot buildHistogram(int bucketCount) {
        CountAtBucket[] histogramData = new CountAtBucket[bucketCount];

        int sampleCount = 0;
        int total = 0;
        int max = 0;
        for (int i = 0; i < bucketCount; i++) {
            sampleCount += ThreadLocalRandom.current().nextInt(1, 5);
            total += sampleCount * i;
            max = Math.max(max, sampleCount > 0 ? i : max);
            histogramData[i] = new CountAtBucket((double) i, sampleCount);
        }

        return new HistogramSnapshot(sampleCount, total, max, null, histogramData, null);
    }


}
