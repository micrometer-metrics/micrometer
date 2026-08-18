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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.DoubleFormat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

@Incubating(since = "1.0.3")
public class HistogramGauges {

    private final AtomicReference<SnapshotState> snapshotState;

    private final HistogramSupport meter;

    private final int totalGauges;

    /**
     * Register a set of gauges for percentiles and histogram buckets that follow a common
     * format when the monitoring system doesn't have an opinion about the structure of
     * this data.
     * @param timer the timer from which to derive gauges
     * @param registry the registry to register the gauges
     * @return registered {@code HistogramGauges}
     */
    public static HistogramGauges registerWithCommonFormat(Timer timer, MeterRegistry registry) {
        return getHistogramGauges(timer, timer.getId(), timer.baseTimeUnit(), registry);
    }

    private static HistogramGauges getHistogramGauges(HistogramSupport histogramSupport, Meter.Id id,
            TimeUnit baseTimeUnit, MeterRegistry registry) {
        return HistogramGauges.register(histogramSupport, registry, percentile -> id.getName() + ".percentile",
                percentile -> Tags.concat(id.getTagsAsIterable(), "phi",
                        DoubleFormat.decimalOrNan(percentile.percentile())),
                percentile -> percentile.value(baseTimeUnit), bucket -> id.getName() + ".histogram",
                bucket -> Tags.concat(id.getTagsAsIterable(), "le",
                        bucket.isPositiveInf() ? "+Inf" : DoubleFormat.wholeOrDecimal(bucket.bucket(baseTimeUnit))));
    }

    /**
     * Register a set of gauges for percentiles and histogram buckets that follow a common
     * format when the monitoring system doesn't have an opinion about the structure of
     * this data.
     * @param ltt the long task timer from which to derive gauges
     * @param registry the registry to register the gauges
     * @return registered {@code HistogramGauges}
     * @since 1.5.0
     */
    public static HistogramGauges registerWithCommonFormat(LongTaskTimer ltt, MeterRegistry registry) {
        return getHistogramGauges(ltt, ltt.getId(), ltt.baseTimeUnit(), registry);
    }

    public static HistogramGauges registerWithCommonFormat(DistributionSummary summary, MeterRegistry registry) {
        Meter.Id id = summary.getId();
        return HistogramGauges.register(summary, registry, percentile -> id.getName() + ".percentile",
                percentile -> Tags.concat(id.getTagsAsIterable(), "phi",
                        DoubleFormat.decimalOrNan(percentile.percentile())),
                ValueAtPercentile::value, bucket -> id.getName() + ".histogram",
                bucket -> Tags.concat(id.getTagsAsIterable(), "le",
                        bucket.isPositiveInf() ? "+Inf" : DoubleFormat.wholeOrDecimal(bucket.bucket())));
    }

    public static HistogramGauges register(HistogramSupport meter, MeterRegistry registry,
            Function<ValueAtPercentile, String> percentileName,
            Function<ValueAtPercentile, Iterable<Tag>> percentileTags,
            Function<ValueAtPercentile, Double> percentileValue, Function<CountAtBucket, String> bucketName,
            Function<CountAtBucket, Iterable<Tag>> bucketTags) {
        return new HistogramGauges(meter, registry, percentileName, percentileTags, percentileValue, bucketName,
                bucketTags);
    }

    private HistogramGauges(HistogramSupport meter, MeterRegistry registry,
            Function<ValueAtPercentile, String> percentileName,
            Function<ValueAtPercentile, Iterable<Tag>> percentileTags,
            Function<ValueAtPercentile, Double> percentileValue, Function<CountAtBucket, String> bucketName,
            Function<CountAtBucket, Iterable<Tag>> bucketTags) {
        this.meter = meter;

        HistogramSnapshot initialSnapshot = meter.takeSnapshot();
        this.snapshotState = new AtomicReference<>(new SnapshotState(initialSnapshot, 0));

        ValueAtPercentile[] valueAtPercentiles = initialSnapshot.percentileValues();
        CountAtBucket[] countAtBuckets = initialSnapshot.histogramCounts();

        this.totalGauges = valueAtPercentiles.length + countAtBuckets.length;

        for (int i = 0; i < valueAtPercentiles.length; i++) {
            ValueAtPercentile registeredPercentile = valueAtPercentiles[i];

            ToDoubleFunction<HistogramSupport> percentileValueFunction = m -> {
                HistogramSnapshot currentSnapshot = pollSnapshot();
                for (ValueAtPercentile currentPercentile : currentSnapshot.percentileValues()) {
                    if (Double.compare(currentPercentile.percentile(), registeredPercentile.percentile()) == 0) {
                        return percentileValue.apply(currentPercentile);
                    }
                }
                return percentileValue
                    .apply(new ValueAtPercentile(registeredPercentile.percentile(), currentSnapshot.max()));
            };

            Gauge.builder(percentileName.apply(valueAtPercentiles[i]), meter, percentileValueFunction)
                .tags(percentileTags.apply(valueAtPercentiles[i]))
                .baseUnit(meter.getId().getBaseUnit())
                .synthetic(meter.getId())
                .register(registry);
        }

        for (int i = 0; i < countAtBuckets.length; i++) {
            CountAtBucket registeredBucket = countAtBuckets[i];

            ToDoubleFunction<HistogramSupport> bucketCountFunction = m -> {
                HistogramSnapshot currentSnapshot = pollSnapshot();
                for (CountAtBucket currentBucket : currentSnapshot.histogramCounts()) {
                    if (sameBucket(currentBucket, registeredBucket)) {
                        return currentBucket.count();
                    }
                }
                return Double.NaN;
            };

            Gauge.builder(bucketName.apply(countAtBuckets[i]), meter, bucketCountFunction)
                .tags(bucketTags.apply(countAtBuckets[i]))
                .synthetic(meter.getId())
                .register(registry);
        }
    }

    private HistogramSnapshot pollSnapshot() {
        while (true) {
            SnapshotState current = snapshotState.get();
            if (current.tryPoll()) {
                return current.snapshot;
            }

            // Concurrent callers may create duplicate candidates, but only one complete
            // snapshot generation can be installed.
            SnapshotState replacement = new SnapshotState(meter.takeSnapshot(), totalGauges);
            if (snapshotState.compareAndSet(current, replacement) && replacement.tryPoll()) {
                return replacement.snapshot;
            }
        }
    }

    private static boolean sameBucket(CountAtBucket current, CountAtBucket registered) {
        return (current.isPositiveInf() && registered.isPositiveInf())
                || Double.compare(current.bucket(), registered.bucket()) == 0;
    }

    private static final class SnapshotState {

        private final HistogramSnapshot snapshot;

        private final AtomicInteger remainingGauges;

        private SnapshotState(HistogramSnapshot snapshot, int remainingGauges) {
            this.snapshot = snapshot;
            this.remainingGauges = new AtomicInteger(remainingGauges);
        }

        private boolean tryPoll() {
            int remaining = remainingGauges.get();
            while (remaining > 0) {
                if (remainingGauges.compareAndSet(remaining, remaining - 1)) {
                    return true;
                }
                remaining = remainingGauges.get();
            }
            return false;
        }

    }

}
