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
 * A Histogram implementation which inherits the behaviour of Step Meters i,e this
 * histogram exhibits read and reset behaviour.
 *
 * @author Lenin Jaganathan
 * @since 1.11.0
 */
public class StepHistogram extends StepValue<CountAtBucket[]> implements Histogram {

    private static final CountAtBucket[] EMPTY_COUNTS = new CountAtBucket[0];

    private final FixedBoundaryHistogram fixedBoundaryHistogram;

    private final double[] buckets;

    public StepHistogram(Clock clock, long stepMillis, DistributionStatisticConfig distributionStatisticConfig) {
        super(clock, stepMillis);

        if (distributionStatisticConfig.getMaximumExpectedValueAsDouble() == null
                || distributionStatisticConfig.getMinimumExpectedValueAsDouble() == null) {
            throw new InvalidConfigurationException(
                    "minimumExpectedValue and maximumExpectedValue should be greater than 0.");
        }
        NavigableSet<Double> histogramBuckets = distributionStatisticConfig.getHistogramBuckets(true);

        this.buckets = histogramBuckets.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        this.fixedBoundaryHistogram = new FixedBoundaryHistogram(buckets);
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
        return new HistogramSnapshot(count, total, max, null, this.poll(), null);
    }

    @Override
    protected Supplier<CountAtBucket[]> valueSupplier() {
        return () -> {
            CountAtBucket[] countAtBuckets = new CountAtBucket[buckets.length];
            synchronized (fixedBoundaryHistogram) {
                for (int i = 0; i < buckets.length; i++) {
                    countAtBuckets[i] = new CountAtBucket(buckets[i], fixedBoundaryHistogram.countAtValue(buckets[i]));
                }
                fixedBoundaryHistogram.reset();
            }
            return countAtBuckets;
        };
    }

    @Override
    protected CountAtBucket[] noValue() {
        if (buckets == null)
            return EMPTY_COUNTS;
        CountAtBucket[] countAtBuckets = new CountAtBucket[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            countAtBuckets[i] = new CountAtBucket(buckets[i], 0);
        }
        return countAtBuckets;
    }

}
