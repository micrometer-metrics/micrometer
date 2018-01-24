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

import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class HistogramSnapshot {

    private static final ValueAtPercentile[] EMPTY_VALUES = new ValueAtPercentile[0];
    private static final CountAtValue[] EMPTY_COUNTS = new CountAtValue[0];
    private static final HistogramSnapshot EMPTY = new HistogramSnapshot(0, 0, 0, null, null);
    private final long count;
    private final double total;
    private final double max;
    private final ValueAtPercentile[] percentileValues;
    private final CountAtValue[] histogramCounts;
    private HistogramSnapshot(long count, double total, double max,
                              @Nullable ValueAtPercentile[] percentileValues,
                              @Nullable CountAtValue[] histogramCounts) {
        this.count = count;
        this.total = total;
        this.max = max;
        this.percentileValues = percentileValues != null ? percentileValues : EMPTY_VALUES;
        this.histogramCounts = histogramCounts != null ? histogramCounts : EMPTY_COUNTS;
    }

    public static HistogramSnapshot of(long count, double total, double max,
                                       @Nullable ValueAtPercentile[] percentileValues,
                                       @Nullable CountAtValue[] histogramCounts) {
        return new HistogramSnapshot(count, total, max, percentileValues, histogramCounts);
    }

    public static HistogramSnapshot empty() {
        return EMPTY;
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

    public CountAtValue[] histogramCounts() {
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
}
