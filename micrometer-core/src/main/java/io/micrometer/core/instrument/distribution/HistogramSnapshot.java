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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.lang.Nullable;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.BiConsumer;

public final class HistogramSnapshot {
    private static final ValueAtPercentile[] EMPTY_VALUES = new ValueAtPercentile[0];
    private static final CountAtBucket[] EMPTY_COUNTS = new CountAtBucket[0];

    private static final HistogramSnapshot EMPTY = new HistogramSnapshot(null, null, null);
    private final ValueAtPercentile[] percentileValues;
    private final CountAtBucket[] histogramCounts;

    @Nullable
    private final BiConsumer<PrintStream, Double> summaryOutput;

    public HistogramSnapshot(@Nullable ValueAtPercentile[] percentileValues,
                             @Nullable CountAtBucket[] histogramCounts,
                             @Nullable BiConsumer<PrintStream, Double> summaryOutput) {
        this.percentileValues = percentileValues != null ? percentileValues : EMPTY_VALUES;
        this.histogramCounts = histogramCounts != null ? histogramCounts : EMPTY_COUNTS;
        this.summaryOutput = summaryOutput;
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

    public static HistogramSnapshot empty() {
        return EMPTY;
    }

    public void outputSummary(PrintStream out, double scale) {
        if(summaryOutput != null) {
            this.summaryOutput.accept(out, scale);
        }
    }
}
