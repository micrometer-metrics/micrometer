/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.util.TimeUtils;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class HistogramSnapshot {

    private static final ValueAtPercentile[] EMPTY_VALUES = new ValueAtPercentile[0];

    private static final CountAtBucket[] EMPTY_COUNTS = new CountAtBucket[0];

    private final ValueAtPercentile[] percentileValues;

    private final CountAtBucket[] histogramCounts;

    private final long count;

    private final double total;

    private final double max;

    @Nullable
    private final BiConsumer<PrintStream, Double> summaryOutput;

    /**
     * @param count Total number of recordings
     * @param total In nanos if a unit of time
     * @param max In nanos if a unit of time
     * @param percentileValues Pre-computed percentiles.
     * @param histogramCounts Bucket counts.
     * @param summaryOutput A function defining how to print the histogram.
     */
    public HistogramSnapshot(long count, double total, double max, @Nullable ValueAtPercentile[] percentileValues,
            @Nullable CountAtBucket[] histogramCounts, @Nullable BiConsumer<PrintStream, Double> summaryOutput) {
        this.count = count;
        this.total = total;
        this.max = max;
        this.percentileValues = percentileValues != null ? percentileValues : EMPTY_VALUES;
        this.histogramCounts = histogramCounts != null ? histogramCounts : EMPTY_COUNTS;
        this.summaryOutput = summaryOutput;
    }

    public long count() {
        return count;
    }

    public double total() {
        return total;
    }

    public double total(TimeUnit unit) {
        return TimeUtils.nanosToUnit(total, unit);
    }

    public double max() {
        return max;
    }

    public double max(TimeUnit unit) {
        return TimeUtils.nanosToUnit(max, unit);
    }

    public double mean() {
        return count == 0 ? 0 : total / count;
    }

    public double mean(TimeUnit unit) {
        return TimeUtils.nanosToUnit(mean(), unit);
    }

    public ValueAtPercentile[] percentileValues() {
        return percentileValues;
    }

    public CountAtBucket[] histogramCounts() {
        return histogramCounts;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("HistogramSnapshot{count=");

        buf.append(count);
        buf.append(", total=");
        buf.append(total);
        buf.append(", mean=");
        buf.append(mean());
        buf.append(", max=");
        buf.append(max);

        if (percentileValues.length > 0) {
            buf.append(", percentileValues=");
            buf.append(Arrays.toString(percentileValues));
        }

        if (histogramCounts.length > 0) {
            buf.append(", histogramCounts=");
            buf.append(Arrays.toString(histogramCounts));
        }

        buf.append('}');
        return buf.toString();
    }

    public static HistogramSnapshot empty(long count, double total, double max) {
        return new HistogramSnapshot(count, total, max, null, null, null);
    }

    public void outputSummary(PrintStream out, double scale) {
        if (summaryOutput != null) {
            this.summaryOutput.accept(out, scale);
        }
    }

}
