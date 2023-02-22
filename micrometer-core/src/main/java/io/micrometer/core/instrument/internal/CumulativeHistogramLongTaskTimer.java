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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extends the default long task timer, making histogram counts cumulative over time.
 *
 * @author Jon Schneider
 * @since 1.5.2
 */
public class CumulativeHistogramLongTaskTimer extends DefaultLongTaskTimer {

    @Nullable
    private CountAtBucket[] lastSnapshot;

    public CumulativeHistogramLongTaskTimer(Id id, Clock clock, TimeUnit baseTimeUnit,
            DistributionStatisticConfig distributionStatisticConfig) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, true);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();

        AtomicInteger i = new AtomicInteger();

        snapshot = new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(),
                snapshot.percentileValues(),
                Arrays.stream(snapshot.histogramCounts())
                    .map(countAtBucket -> lastSnapshot == null ? countAtBucket
                            : new CountAtBucket(countAtBucket.bucket(),
                                    countAtBucket.count() + lastSnapshot[i.getAndIncrement()].count()))
                    .toArray(CountAtBucket[]::new),
                snapshot::outputSummary);

        lastSnapshot = snapshot.histogramCounts();
        return snapshot;
    }

}
