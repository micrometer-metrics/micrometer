/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;

import java.util.concurrent.TimeUnit;

/**
 * A {@link DefaultLongTaskTimer} that publishes the distribution of its active tasks as a
 * base2 exponential histogram, for when
 * {@link HistogramFlavor#BASE2_EXPONENTIAL_BUCKET_HISTOGRAM} is configured. Explicit
 * bucket counts are not produced, so this is used for both aggregation temporalities.
 */
class OtlpExponentialHistogramLongTaskTimer extends DefaultLongTaskTimer
        implements OtlpHistogramSupport, StartTimeAwareMeter {

    private final Base2ExponentialHistogram exponentialHistogram;

    private final boolean resetBeforeSnapshot;

    private final long startTimeNanos;

    /**
     * @param exponentialHistogram histogram the active task durations are recorded into
     * @param resetBeforeSnapshot whether the recorded values are discarded before each
     * snapshot. A task leaves the distribution once it finishes, so its duration has to
     * be recorded again on every snapshot, and a delta export starts each snapshot from
     * an empty histogram. A cumulative export instead keeps adding to what was already
     * recorded, which is what the explicit bucket counts of
     * {@link CumulativeHistogramLongTaskTimer} do.
     */
    OtlpExponentialHistogramLongTaskTimer(Id id, Clock clock, TimeUnit baseTimeUnit,
            DistributionStatisticConfig distributionStatisticConfig, Base2ExponentialHistogram exponentialHistogram,
            boolean resetBeforeSnapshot) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, false);
        this.exponentialHistogram = exponentialHistogram;
        this.resetBeforeSnapshot = resetBeforeSnapshot;
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        // The meter polling thread and the publishing thread both take snapshots. The
        // histogram records, resets and snapshots under its own monitor, so taking that
        // monitor here makes the whole snapshot atomic against a concurrent one.
        synchronized (this.exponentialHistogram) {
            if (this.resetBeforeSnapshot) {
                this.exponentialHistogram.reset();
            }
            forEachActive(sample -> {
                double duration = sample.duration(TimeUnit.NANOSECONDS);
                // a task that stopped while we were iterating reports -1
                if (duration >= 0) {
                    this.exponentialHistogram.recordDouble(duration);
                }
            });
            this.exponentialHistogram.takeExponentialHistogramSnapShot();
            return super.takeSnapshot();
        }
    }

    @Override
    public ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
        synchronized (this.exponentialHistogram) {
            return this.exponentialHistogram.getLatestExponentialHistogramSnapshot();
        }
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

}
