/*
 * Copyright 2020 VMware, Inc.
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

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * A histogram implementation for non-negative values with automatically created buckets.
 * It does not support precomputed percentiles but supports aggregable percentile
 * histograms. It's suitable only with VictoriaMetrics storage.
 *
 * <a href="https://github.com/VictoriaMetrics/metrics/blob/master/histogram.go">Reference
 * implementation</a> written in Go originally by Aliaksandr Valialkin.
 *
 * @author Aliaksandr Valialkin
 * @author Nikolay Ustinov
 * @since 1.4.0
 */
public class FixedBoundaryVictoriaMetricsHistogram implements Histogram {

    private static final IdxOffset UPPER = new IdxOffset(-1, 2);

    private static final IdxOffset LOWER = new IdxOffset(-1, 1);

    private static final IdxOffset ZERO = new IdxOffset(-1, 0);

    private static final int E10MIN = -9;

    private static final int E10MAX = 18;

    private static final int DECIMAL_MULTIPLIER = 2;

    private static final int BUCKET_SIZE = 9 * DECIMAL_MULTIPLIER;

    private static final int BUCKETS_COUNT = E10MAX - E10MIN;

    private static final double DECIMAL_PRECISION = 0.01 / DECIMAL_MULTIPLIER;

    private static final String[] VMRANGES;

    private static final double[] UPPER_BOUNDS;

    static {
        VMRANGES = new String[3 + BUCKETS_COUNT * BUCKET_SIZE];
        VMRANGES[0] = "0...0";
        VMRANGES[1] = String.format(Locale.US, "0...%.1fe%d", 1.0, E10MIN);
        VMRANGES[2] = String.format(Locale.US, "%.1fe%d...+Inf", 1.0, E10MAX);

        UPPER_BOUNDS = new double[3 + BUCKETS_COUNT * BUCKET_SIZE];
        UPPER_BOUNDS[0] = 0.0;
        UPPER_BOUNDS[1] = BigDecimal.TEN.pow(E10MIN, MathContext.DECIMAL128).doubleValue();
        UPPER_BOUNDS[2] = Double.POSITIVE_INFINITY;

        int idx = 3;
        String start = String.format(Locale.US, "%.1fe%d", 1.0, E10MIN);

        for (int bucketIdx = 0; bucketIdx < BUCKETS_COUNT; bucketIdx++) {
            for (int offset = 0; offset < BUCKET_SIZE; offset++) {
                int e10 = E10MIN + bucketIdx;
                double m = 1 + (double) (offset + 1) / DECIMAL_MULTIPLIER;
                if (Math.abs(m - 10) < DECIMAL_PRECISION) {
                    m = 1;
                    e10++;
                }
                String end = String.format(Locale.US, "%.1fe%d", m, e10);
                VMRANGES[idx] = start + "..." + end;

                UPPER_BOUNDS[idx] = BigDecimal.valueOf(m)
                    .setScale(1, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.TEN.pow(e10, MathContext.DECIMAL128))
                    .doubleValue();

                idx++;
                start = end;
            }
        }
    }

    final AtomicReferenceArray<AtomicLongArray> values;

    final AtomicLong zeros;

    final AtomicLong lower;

    final AtomicLong upper;

    final DoubleAdder sum;

    public FixedBoundaryVictoriaMetricsHistogram() {
        this.zeros = new AtomicLong();
        this.lower = new AtomicLong();
        this.upper = new AtomicLong();
        this.sum = new DoubleAdder();

        this.values = new AtomicReferenceArray<>(BUCKETS_COUNT);
    }

    @Override
    public void recordLong(long value) {
        recordDouble((double) value);
    }

    @Override
    public void recordDouble(double value) {
        if (Double.isNaN(value) || value < 0)
            return;
        IdxOffset inxs = getBucketIdxAndOffset(value);
        sum.add(value);
        if (inxs.bucketIdx < 0) {
            if (inxs.offset == 0)
                zeros.incrementAndGet();
            else if (inxs.offset == 1)
                lower.incrementAndGet();
            else
                upper.incrementAndGet();
            return;
        }
        AtomicLongArray hb = values.get(inxs.bucketIdx);
        if (hb == null) {
            hb = new AtomicLongArray(BUCKET_SIZE);
            if (!values.compareAndSet(inxs.bucketIdx, null, hb))
                hb = values.get(inxs.bucketIdx);
        }

        hb.incrementAndGet(inxs.offset);
    }

    private static IdxOffset getBucketIdxAndOffset(double value) {
        if (value < 0)
            throw new RuntimeException(String.format("BUG: v must be positive; got %f", value));
        if (value == 0)
            return ZERO;
        if (Double.POSITIVE_INFINITY == value)
            return UPPER;

        int e10 = (int) Math.floor(Math.log10(value));
        int bucketIdx = e10 - E10MIN;
        if (bucketIdx < 0)
            return LOWER;

        double pow = Math.pow(10, e10);
        if (bucketIdx >= BUCKETS_COUNT) {
            if ((bucketIdx == BUCKETS_COUNT) && (Math.abs(pow - value) < DECIMAL_PRECISION)) {
                // Adjust m to be on par with Prometheus 'le' buckets (aka 'less or
                // equal')
                return new IdxOffset(BUCKETS_COUNT - 1, BUCKET_SIZE - 1);
            }
            return UPPER;
        }

        double m = ((value / pow) - 1) * DECIMAL_MULTIPLIER;
        int offset = (int) m;
        if (offset < 0)
            offset = 0;
        else if (offset >= BUCKET_SIZE)
            offset = BUCKET_SIZE - 1;

        if (Math.abs((double) offset - m) < DECIMAL_PRECISION) {
            // Adjust offset to be on par with Prometheus 'le' buckets (aka 'less or
            // equal')
            offset--;
            if (offset < 0) {
                bucketIdx--;
                if (bucketIdx < 0)
                    return LOWER;
                offset = BUCKET_SIZE - 1;
            }
        }

        return new IdxOffset(bucketIdx, offset);
    }

    private static int getRangeIndex(int index, int offset) {
        if (index < 0) {
            if (offset > 2)
                throw new RuntimeException(
                        String.format("BUG: offset must be in range [0...2] for negative bucketIdx; got %d", offset));
            return offset;
        }
        return 3 + index * BUCKET_SIZE + offset;
    }

    public static String getRangeTagValue(double value) {
        IdxOffset idxOffset = getBucketIdxAndOffset(value);
        return VMRANGES[getRangeIndex(idxOffset.bucketIdx, idxOffset.offset)];
    }

    private List<CountAtBucket> nonZeroBuckets() {
        List<CountAtBucket> buckets = new ArrayList<>();

        long zeroSnap = zeros.get();
        if (zeroSnap > 0) {
            buckets.add(new CountAtBucket(UPPER_BOUNDS[getRangeIndex(ZERO.bucketIdx, ZERO.offset)], zeroSnap));
        }

        long lowerSnap = lower.get();
        if (lowerSnap > 0) {
            buckets.add(new CountAtBucket(UPPER_BOUNDS[getRangeIndex(LOWER.bucketIdx, LOWER.offset)], lowerSnap));
        }

        long upperSnap = upper.get();
        if (upperSnap > 0) {
            buckets.add(new CountAtBucket(UPPER_BOUNDS[getRangeIndex(UPPER.bucketIdx, UPPER.offset)], upperSnap));
        }

        for (int i = 0; i < values.length(); i++) {
            AtomicLongArray bucket = values.get(i);
            if (bucket != null) {
                for (int j = 0; j < bucket.length(); j++) {
                    long cnt = bucket.get(j);
                    if (cnt > 0) {
                        buckets.add(new CountAtBucket(UPPER_BOUNDS[getRangeIndex(i, j)], cnt));
                    }
                }
            }
        }

        return buckets;
    }

    @Override
    public HistogramSnapshot takeSnapshot(long count, double total, double max) {
        return new HistogramSnapshot(count, total, max, null, nonZeroBuckets().toArray(new CountAtBucket[0]),
                this::outputSummary);
    }

    private void outputSummary(PrintStream printStream, double bucketScaling) {
        printStream.format("%14s %10s\n\n", "Bucket", "TotalCount");

        for (CountAtBucket bucket : nonZeroBuckets()) {
            printStream.format(Locale.US, "%14.1f %10d\n", bucket.bucket() / bucketScaling, bucket.count());
        }

        printStream.write('\n');
    }

    private static class IdxOffset {

        final int bucketIdx;

        final int offset;

        IdxOffset(int bucketIdx, int offset) {
            this.bucketIdx = bucketIdx;
            this.offset = offset;
        }

    }

}
