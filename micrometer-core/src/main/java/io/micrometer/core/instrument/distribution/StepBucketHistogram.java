/*
 * Copyright 2023 VMware, Inc.
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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.micrometer.core.instrument.step.StepValue;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A Histogram implementation which inherits the behaviour of step meters, i.e. read and
 * reset behaviour.
 *
 * @author Lenin Jaganathan
 * @since 1.11.0
 */
public class StepBucketHistogram extends StepValue<CountAtBucket[]> implements Histogram {

    private final FixedBoundaryHistogram fixedBoundaryHistogram;

    public StepBucketHistogram(Clock clock, long stepMillis, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts) {
        super(clock, stepMillis, getEmptyCounts(
                getBucketsFromDistributionStatisticConfig(distributionStatisticConfig, supportsAggregablePercentiles)));

        this.fixedBoundaryHistogram = new FixedBoundaryHistogram(
                getBucketsFromDistributionStatisticConfig(distributionStatisticConfig, supportsAggregablePercentiles),
                isCumulativeBucketCounts);
    }

    @Override
    public void recordLong(long value) {
        fixedBoundaryHistogram.record(value);
    }

    @Override
    public void recordDouble(double value) {
        recordLong((long) Math.ceil(value));
    }

    @Override
    public HistogramSnapshot takeSnapshot(long count, double total, double max) {
        return new HistogramSnapshot(count, total, max, null, poll(), null);
    }

    @Override
    protected Supplier<CountAtBucket[]> valueSupplier() {
        return () -> {
            CountAtBucket[] countAtBuckets;
            synchronized (fixedBoundaryHistogram) {
                countAtBuckets = fixedBoundaryHistogram.getCountAtBuckets();
                fixedBoundaryHistogram.reset();
            }
            return countAtBuckets;
        };
    }

    @Override
    protected CountAtBucket[] noValue() {
        return getEmptyCounts(this.fixedBoundaryHistogram.getBuckets());
    }

    private static CountAtBucket[] getEmptyCounts(double[] buckets) {
        CountAtBucket[] countAtBuckets = new CountAtBucket[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            countAtBuckets[i] = new CountAtBucket(buckets[i], 0);
        }
        return countAtBuckets;
    }

    private static double[] getBucketsFromDistributionStatisticConfig(
            DistributionStatisticConfig distributionStatisticConfig, boolean supportsAggregablePercentiles) {
        if (distributionStatisticConfig.getMaximumExpectedValueAsDouble() == null
                || distributionStatisticConfig.getMinimumExpectedValueAsDouble() == null
                || distributionStatisticConfig.getMaximumExpectedValueAsDouble() <= 0
                || distributionStatisticConfig.getMinimumExpectedValueAsDouble() <= 0) {
            throw new InvalidConfigurationException(
                    "minimumExpectedValue and maximumExpectedValue should be greater than 0.");
        }
        NavigableSet<Double> histogramBuckets = distributionStatisticConfig
            .getHistogramBuckets(supportsAggregablePercentiles);

        return histogramBuckets.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
    }

}
