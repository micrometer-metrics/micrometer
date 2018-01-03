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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.CountAtValue;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.TimeWindowLatencyHistogram;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeDecayingMax;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class PrometheusTimer extends AbstractTimer implements Timer {
    private final LongAdder count = new LongAdder();
    private final LongAdder totalTime = new LongAdder();
    private final TimeDecayingMax max;
    private final TimeWindowLatencyHistogram percentilesHistogram;

    PrometheusTimer(Id id, Clock clock, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        super(id, clock, histogramConfig, pauseDetector, TimeUnit.SECONDS);
        this.max = new TimeDecayingMax(clock, histogramConfig);

        this.percentilesHistogram = new TimeWindowLatencyHistogram(clock,
            HistogramConfig.builder()
                .histogramExpiry(Duration.ofDays(1825)) // effectively never roll over
                .histogramBufferLength(1)
                .build()
                .merge(histogramConfig), pauseDetector);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        count.increment();
        long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
        totalTime.add(nanoAmount);
        percentilesHistogram.recordLong(nanoAmount);
        max.record(nanoAmount, TimeUnit.NANOSECONDS);
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.max(unit);
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is based on a
     * rolling histogram. Prometheus requires a histogram that accumulates values over the lifetime of the app.
     */
    public CountAtValue[] percentileBuckets() {
        return percentilesHistogram.takeSnapshot(0, 0, 0, true).histogramCounts();
    }
}
