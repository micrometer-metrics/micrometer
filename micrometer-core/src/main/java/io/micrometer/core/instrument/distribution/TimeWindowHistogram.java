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

import io.micrometer.core.instrument.Clock;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;

/**
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
public class TimeWindowHistogram extends TimeWindowHistogramBase<DoubleRecorder, DoubleHistogram> {

    private final DoubleHistogram intervalHistogram;

    public TimeWindowHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig) {
        super(clock, distributionStatisticConfig, DoubleRecorder.class);
        intervalHistogram = new DoubleHistogram(NUM_SIGNIFICANT_VALUE_DIGITS);
        initRingBuffer();
    }

    @Override
    DoubleRecorder newBucket(DistributionStatisticConfig distributionStatisticConfig) {
        return new DoubleRecorder(NUM_SIGNIFICANT_VALUE_DIGITS);
    }

    @Override
    void recordDouble(DoubleRecorder bucket, double value) {
        bucket.recordValue(value);
    }

    @Override
    void recordLong(DoubleRecorder bucket, long value) {
        bucket.recordValue(value);
    }

    @Override
    void resetBucket(DoubleRecorder bucket) {
        bucket.reset();
    }

    @Override
    DoubleHistogram newAccumulatedHistogram(DoubleRecorder[] ringBuffer) {
        return new DoubleHistogram(NUM_SIGNIFICANT_VALUE_DIGITS);
    }

    @Override
    void accumulate(DoubleRecorder sourceBucket, DoubleHistogram accumulatedHistogram) {
        sourceBucket.getIntervalHistogramInto(intervalHistogram);
        accumulatedHistogram.add(intervalHistogram);
    }

    @Override
    void resetAccumulatedHistogram(DoubleHistogram accumulatedHistogram) {
        accumulatedHistogram.reset();
    }

    @Override
    double valueAtPercentile(DoubleHistogram accumulatedHistogram, double percentile) {
        return accumulatedHistogram.getValueAtPercentile(percentile);
    }

    @Override
    double countAtValue(DoubleHistogram accumulatedHistogram, long value) {
        return accumulatedHistogram.getCountBetweenValues(0, value);
    }
}
