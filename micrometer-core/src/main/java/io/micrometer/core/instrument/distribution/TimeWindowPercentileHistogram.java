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

import io.micrometer.core.instrument.Clock;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;

import java.io.PrintStream;

/**
 * <b>NOTE: This class is intended for internal use as an implementation detail. You
 * should not compile against its API. Please contact the project maintainers if you need
 * this as public API.</b>
 * <p>
 * A histogram implementation that supports the computation of percentiles by Micrometer
 * for publishing to a monitoring system.
 *
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
public class TimeWindowPercentileHistogram extends AbstractTimeWindowHistogram<DoubleRecorder, DoubleHistogram> {

    private final DoubleHistogram intervalHistogram;

    public TimeWindowPercentileHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles) {
        super(clock, distributionStatisticConfig, DoubleRecorder.class, supportsAggregablePercentiles);
        intervalHistogram = new DoubleHistogram(percentilePrecision(distributionStatisticConfig));
        initRingBuffer();
    }

    @Override
    DoubleRecorder newBucket() {
        return new DoubleRecorder(percentilePrecision(distributionStatisticConfig));
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
        return new DoubleHistogram(percentilePrecision(distributionStatisticConfig));
    }

    @Override
    void accumulate() {
        currentHistogram().getIntervalHistogramInto(intervalHistogram);
        accumulatedHistogram().add(intervalHistogram);
    }

    @Override
    void resetAccumulatedHistogram() {
        accumulatedHistogram().reset();
    }

    @Override
    double valueAtPercentile(double percentile) {
        return accumulatedHistogram().getValueAtPercentile(percentile);
    }

    @Override
    double countAtValue(double value) {
        return accumulatedHistogram().getCountBetweenValues(0, value);
    }

    private int percentilePrecision(DistributionStatisticConfig config) {
        return config.getPercentilePrecision() == null ? 1 : config.getPercentilePrecision();
    }

    @Override
    void outputSummary(PrintStream out, double bucketScaling) {
        accumulatedHistogram().outputPercentileDistribution(out, bucketScaling);
    }

}
