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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.TimeWindowLatencyHistogram;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractTimer extends AbstractMeter implements Timer {
    protected final Clock clock;
    private final HistogramConfig histogramConfig;
    protected final TimeWindowLatencyHistogram histogram;

    protected AbstractTimer(Meter.Id id, Clock clock, HistogramConfig histogramConfig) {
        super(id);
        this.clock = clock;
        this.histogramConfig = histogramConfig;
        this.histogram = new TimeWindowLatencyHistogram(clock, histogramConfig);
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return () -> {
            final long s = clock.monotonicTime();
            try {
                return f.call();
            } finally {
                final long e = clock.monotonicTime();
                record(e - s, TimeUnit.NANOSECONDS);
            }
        };
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public final void record(long amount, TimeUnit unit) {
        if(amount >= 0) {
            histogram.recordLong(TimeUnit.NANOSECONDS.convert(amount, unit));
            recordNonNegative(amount, unit);
        }
    }

    protected abstract void recordNonNegative(long amount, TimeUnit unit);

    @Override
    public double percentile(double percentile, TimeUnit unit) {
        return histogram.percentile(percentile, unit);
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        return histogram.histogramCountAtValue(valueNanos);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return histogram.takeSnapshot(count(), totalTime(TimeUnit.NANOSECONDS), max(TimeUnit.NANOSECONDS),
                                      supportsAggregablePercentiles);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    public HistogramConfig statsConfig() {
        return histogramConfig;
    }
}
