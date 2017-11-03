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
package io.micrometer.core.instrument.histogram;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.util.TimeUtils;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowLatencyHistogramTest {
    @Test
    void histogramsAreCumulative() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(Clock.SYSTEM, HistogramConfig.DEFAULT);
        histogram.record(1);
        histogram.record(2);

        assertThat(histogram.histogramCountAtValue(1)).isEqualTo(1);
        assertThat(histogram.histogramCountAtValue(2)).isEqualTo(2);
        assertThat(histogram.histogramCountAtValue(3)).isEqualTo(2);
    }

    @Test
    void sampleValueAboveMaximumExpectedValue() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(Clock.SYSTEM, HistogramConfig.builder()
            .maximumExpectedValue(2L)
            .build()
            .merge(HistogramConfig.DEFAULT));
        histogram.record(3);
        assertThat(histogram.histogramCountAtValue(3)).isEqualTo(1);
        assertThat(histogram.histogramCountAtValue(Long.MAX_VALUE)).isEqualTo(1);
    }

    @Test
    void recordValuesThatExceedTheDynamicRange() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(new MockClock(), HistogramConfig.builder()
            .minimumExpectedValue(1L)
            .maximumExpectedValue(100L)
            .build()
            .merge(HistogramConfig.DEFAULT));

        // Always too large, regardless of bounds.
        histogram.record(Long.MAX_VALUE);
    }

    @Test
    void percentiles() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(new MockClock(), HistogramConfig.DEFAULT);

        for(int i = 1; i <= 10; i++) {
            histogram.record((long) TimeUtils.millisToUnit(i, TimeUnit.NANOSECONDS));
        }

        assertThat(histogram.percentile(0.5, TimeUnit.MILLISECONDS)).isEqualTo(5, Offset.offset(0.1));
        assertThat(histogram.percentile(0.9, TimeUnit.MILLISECONDS)).isEqualTo(9, Offset.offset(0.1));
        assertThat(histogram.percentile(0.95, TimeUnit.MILLISECONDS)).isEqualTo(10, Offset.offset(0.1));
    }

    @Test
    void percentilesWithNoSamples() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(new MockClock(), HistogramConfig.DEFAULT);
        assertThat(histogram.percentile(0.5, TimeUnit.MILLISECONDS)).isEqualTo(0);
    }

    @Test
    void percentilesChangeWithMoreRecentSamples() {
        TimeWindowLatencyHistogram histogram = new TimeWindowLatencyHistogram(new MockClock(), HistogramConfig.DEFAULT);

        for(int i = 1; i <= 10; i++) {
            histogram.record((long) TimeUtils.millisToUnit(i, TimeUnit.NANOSECONDS));
        }

        // baseline median
        assertThat(histogram.percentile(0.50, TimeUnit.MILLISECONDS)).isEqualTo(5, Offset.offset(0.1));

        for(int i = 11; i <= 20; i++) {
            histogram.record((long) TimeUtils.millisToUnit(i, TimeUnit.NANOSECONDS));
        }

        // median should have moved after seeing 10 more samples
        assertThat(histogram.percentile(0.50, TimeUnit.MILLISECONDS)).isEqualTo(10, Offset.offset(0.1));
    }
}