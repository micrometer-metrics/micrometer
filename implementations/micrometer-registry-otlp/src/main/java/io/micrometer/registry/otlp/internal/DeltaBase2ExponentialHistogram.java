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
package io.micrometer.registry.otlp.internal;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepValue;

/**
 * A {@link Base2ExponentialHistogram} where values are reset after every Step.
 * Internally, this uses {@link StepValue} to roll the HistogramSnapshot for every step.
 * <p>
 * <strong> This is an internal class and might have breaking changes, external
 * implementations SHOULD NOT rely on this implementation. </strong>
 * </p>
 *
 * @author Lenin Jaganathan
 * @since 1.14.0
 */
public class DeltaBase2ExponentialHistogram extends Base2ExponentialHistogram {

    private final StepExponentialHistogramSnapShot stepExponentialHistogramSnapShot;

    /**
     * Creates an Base2ExponentialHistogram that record positive values and resets for
     * every step. This doesn't move the step window during recording but this does so on
     * calling {@link Base2ExponentialHistogram#takeSnapshot(long, double, double)} ()}.
     * @param maxScale - maximum scale that can be used. The recordings start with this
     * scale and gets downscaled to the scale that supports recording data within
     * maxBucketsCount.
     * @param maxBucketsCount - maximum number of buckets that can be used for
     * distribution.
     * @param zeroThreshold - values less than or equal to this are considered in zero
     * count and recorded in the histogram. If less than 0, this is rounded to zero. In
     * case of recording time, this should be in nanoseconds.
     * @param baseUnit - an Optional TimeUnit. If set to a non-null unit, the recorded
     * values are converted to this unit.
     * @param clock - clock to be used.
     * @param stepMillis - window for delta aggregation
     */
    public DeltaBase2ExponentialHistogram(final int maxScale, final int maxBucketsCount, final double zeroThreshold,
            @Nullable final TimeUnit baseUnit, final Clock clock, final long stepMillis) {
        super(maxScale, maxBucketsCount, zeroThreshold, baseUnit);
        this.stepExponentialHistogramSnapShot = new StepExponentialHistogramSnapShot(clock, stepMillis, maxScale);
    }

    @Override
    public ExponentialHistogramSnapShot getLatestExponentialHistogramSnapshot() {
        return stepExponentialHistogramSnapShot.poll();
    }

    @Override
    synchronized void takeExponentialHistogramSnapShot() {
        stepExponentialHistogramSnapShot.poll();
    }

    @Override
    public void close() {
        stepExponentialHistogramSnapShot._closingRollover();
    }

    private class StepExponentialHistogramSnapShot extends StepValue<ExponentialHistogramSnapShot> {

        public StepExponentialHistogramSnapShot(final Clock clock, final long stepMillis, final int maxScale) {
            super(clock, stepMillis, DefaultExponentialHistogramSnapShot.getEmptySnapshotForScale(maxScale));
        }

        @Override
        protected synchronized Supplier<ExponentialHistogramSnapShot> valueSupplier() {
            return () -> {
                ExponentialHistogramSnapShot latestSnapShot = getCurrentValuesSnapshot();
                reset();
                return latestSnapShot;
            };
        }

        @Override
        protected ExponentialHistogramSnapShot noValue() {
            return DefaultExponentialHistogramSnapShot.getEmptySnapshotForScale(getScale());
        }

        @Override
        protected void _closingRollover() {
            super._closingRollover();
        }

    }

}
