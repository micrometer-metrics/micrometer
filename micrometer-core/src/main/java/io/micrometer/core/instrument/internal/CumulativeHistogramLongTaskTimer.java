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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.concurrent.TimeUnit;

/**
 * Extends the default long task timer, making histogram counts cumulative over time.
 * <p>
 * The counts reported by the superclass cover only the tasks that are active when the
 * snapshot is taken, so a bucket loses a task as soon as that task finishes or ages past
 * the bucket. This timer carries the counts it published before forward and adds the
 * tasks that entered each bucket since, so the finite buckets never decrease. That is
 * what OTLP needs under cumulative temporality, and it is also what the OpenTSDB and
 * simpleclient Prometheus registries use. The current Prometheus registry does not use
 * this class, it reports a gauge histogram of the active tasks instead.
 * <p>
 * The counts are an approximation. Tasks are counted from the change in each bucket's
 * active count between two snapshots, which is arrivals minus departures, so a task that
 * enters a bucket in the same interval as another leaves it is not counted at all. A task
 * is credited to the bucket it was first seen in, so the shape of the histogram reflects
 * how long after a task started the snapshot happened to be taken rather than how long
 * the task ran, and a bucket narrower than the interval between snapshots reports far
 * fewer tasks than passed through it, possibly none at all.
 * <p>
 * Only the finite buckets are cumulative. {@link HistogramSnapshot#count()},
 * {@link HistogramSnapshot#total()} and {@link HistogramSnapshot#max()} still describe
 * the active tasks, and the registries above derive the {@code +Inf} bucket from
 * {@code count()}, so that bucket still returns to zero and can sit below the finite
 * buckets.
 *
 * @author Jon Schneider
 * @since 1.5.2
 */
public class CumulativeHistogramLongTaskTimer extends DefaultLongTaskTimer {

    private final Object lock = new Object();

    private double[] lastActiveCounts = {};

    private double[] cumulativeCounts = {};

    public CumulativeHistogramLongTaskTimer(Id id, Clock clock, TimeUnit baseTimeUnit,
            DistributionStatisticConfig distributionStatisticConfig) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, true);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        // The delegate snapshot is taken under the lock as well, since applying two
        // snapshots out of chronological order would measure one of them against the
        // other's active counts and overcount.
        synchronized (lock) {
            HistogramSnapshot snapshot = super.takeSnapshot();
            CountAtBucket[] activeCounts = snapshot.histogramCounts();
            if (lastActiveCounts.length != activeCounts.length) {
                // The bucket count is derived from an immutable
                // DistributionStatisticConfig, so this allocates on the first snapshot
                // and does not run again.
                lastActiveCounts = new double[activeCounts.length];
                cumulativeCounts = new double[activeCounts.length];
            }

            CountAtBucket[] countAtBuckets = new CountAtBucket[activeCounts.length];
            // The change in a bucket's active count is arrivals minus departures, so it
            // underestimates how many tasks entered, and a net departure is clamped to
            // zero so that the published count never goes backwards. A task that entered
            // a bucket also entered every wider one, so each bucket takes the largest
            // increase seen at or below it.
            double entered = 0;
            for (int i = 0; i < activeCounts.length; i++) {
                double activeCount = activeCounts[i].count();
                entered = Math.max(entered, activeCount - lastActiveCounts[i]);
                lastActiveCounts[i] = activeCount;
                cumulativeCounts[i] += entered;
                countAtBuckets[i] = new CountAtBucket(activeCounts[i].bucket(), cumulativeCounts[i]);
            }

            return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(),
                    snapshot.percentileValues(), countAtBuckets, snapshot::outputSummary);
        }
    }

}
