/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.DoubleFormat;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

@Incubating(since = "1.0.3")
public class HistogramGauges {
    // How many gauges have been polled so far on this publish cycle
    volatile CountDownLatch polledGaugesLatch;

    private volatile HistogramSnapshot snapshot;

    private final HistogramSupport meter;
    private final int totalGauges;

    /**
     * Register a set of gauges for percentiles and histogram buckets that follow a common format when
     * the monitoring system doesn't have an opinion about the structure of this data.
     *
     * @param timer the timer from which to derive gauges
     * @param registry the registry to register the gauges
     * @return registered {@code HistogramGauges}
     */
    public static HistogramGauges registerWithCommonFormat(Timer timer, MeterRegistry registry) {
        Meter.Id id = timer.getId();
        return HistogramGauges.register(timer, registry,
                percentile -> id.getName() + ".percentile",
                percentile -> Tags.concat(id.getTagsAsIterable(), "phi", DoubleFormat.decimalOrNan(percentile.percentile())),
                percentile -> percentile.value(timer.baseTimeUnit()),
                bucket -> id.getName() + ".histogram",
                // We look for Long.MAX_VALUE to ensure a sensible tag on our +Inf bucket
                bucket -> Tags.concat(id.getTagsAsIterable(), "le", bucket.bucket() != Long.MAX_VALUE
                        ? DoubleFormat.wholeOrDecimal(bucket.bucket(timer.baseTimeUnit())) : "+Inf"));
    }

    public static HistogramGauges registerWithCommonFormat(DistributionSummary summary, MeterRegistry registry) {
        Meter.Id id = summary.getId();
        return HistogramGauges.register(summary, registry,
                percentile -> id.getName() + ".percentile",
                percentile -> Tags.concat(id.getTagsAsIterable(), "phi", DoubleFormat.decimalOrNan(percentile.percentile())),
                ValueAtPercentile::value,
                bucket -> id.getName() + ".histogram",
                // We look for Long.MAX_VALUE to ensure a sensible tag on our +Inf bucket
                bucket -> Tags.concat(id.getTagsAsIterable(), "le", bucket.bucket() != Long.MAX_VALUE
                        ? DoubleFormat.wholeOrDecimal(bucket.bucket()) : "+Inf"));
    }

    public static HistogramGauges register(HistogramSupport meter, MeterRegistry registry,
                                           Function<ValueAtPercentile, String> percentileName,
                                           Function<ValueAtPercentile, Iterable<Tag>> percentileTags,
                                           Function<ValueAtPercentile, Double> percentileValue,
                                           Function<CountAtBucket, String> bucketName,
                                           Function<CountAtBucket, Iterable<Tag>> bucketTags) {
        return new HistogramGauges(meter, registry, percentileName, percentileTags, percentileValue, bucketName, bucketTags);
    }

    private HistogramGauges(HistogramSupport meter, MeterRegistry registry,
                            Function<ValueAtPercentile, String> percentileName,
                            Function<ValueAtPercentile, Iterable<Tag>> percentileTags,
                            Function<ValueAtPercentile, Double> percentileValue,
                            Function<CountAtBucket, String> bucketName,
                            Function<CountAtBucket, Iterable<Tag>> bucketTags) {
        this.meter = meter;

        HistogramSnapshot initialSnapshot = meter.takeSnapshot();
        this.snapshot = initialSnapshot;

        ValueAtPercentile[] valueAtPercentiles = initialSnapshot.percentileValues();
        CountAtBucket[] countAtBuckets = initialSnapshot.histogramCounts();

        this.totalGauges = valueAtPercentiles.length + countAtBuckets.length;

        // set to zero initially, so the first polling of one of the gauges on each publish cycle results in a
        // new snapshot
        this.polledGaugesLatch = new CountDownLatch(0);

        for (int i = 0; i < valueAtPercentiles.length; i++) {
            final int index = i;

            ToDoubleFunction<HistogramSupport> percentileValueFunction = m -> {
                snapshotIfNecessary();
                polledGaugesLatch.countDown();
                return percentileValue.apply(snapshot.percentileValues()[index]);
            };

            Gauge.builder(percentileName.apply(valueAtPercentiles[i]), meter, percentileValueFunction)
                    .tags(percentileTags.apply(valueAtPercentiles[i]))
                    .baseUnit(meter.getId().getBaseUnit())
                    .synthetic(meter.getId())
                    .register(registry);
        }

        for (int i = 0; i < countAtBuckets.length; i++) {
            final int index = i;

            ToDoubleFunction<HistogramSupport> bucketCountFunction = m -> {
                snapshotIfNecessary();
                polledGaugesLatch.countDown();
                return snapshot.histogramCounts()[index].count();
            };

            Gauge.builder(bucketName.apply(countAtBuckets[i]), meter, bucketCountFunction)
                    .tags(bucketTags.apply(countAtBuckets[i]))
                    .synthetic(meter.getId())
                    .register(registry);
        }
    }

    private void snapshotIfNecessary() {
        if (polledGaugesLatch.getCount() == 0) {
            snapshot = meter.takeSnapshot();
            polledGaugesLatch = new CountDownLatch(totalGauges);
        }
    }
}
