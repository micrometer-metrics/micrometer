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
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

public class AggregateTimer extends AggregateMeter implements Timer {
    private final Collection<Timer> timers;

    AggregateTimer(Id aggregateId, Collection<Timer> timers) {
        super(aggregateId);
        this.timers = timers;
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        Collection<HistogramSnapshot> snapshots = timers.stream()
                .map(t -> t.takeSnapshot(supportsAggregablePercentiles))
                .collect(toList());

        Map<Long, Double> countAtBucket = null;
        for (HistogramSnapshot snapshot : snapshots) {
            Map<Long, Double> bucketsAsMap = new HashMap<>();

            for (CountAtBucket cab : snapshot.histogramCounts()) {
                bucketsAsMap.put(cab.bucket(), cab.count());
            }

            if (countAtBucket == null) {
                countAtBucket = bucketsAsMap;
            } else {
                countAtBucket.keySet().retainAll(bucketsAsMap.keySet());
                for (Map.Entry<Long, Double> bucketAndCount : countAtBucket.entrySet()) {
                    Long bucket = bucketAndCount.getKey();
                    countAtBucket.put(bucket, bucketAndCount.getValue() + bucketsAsMap.get(bucket));
                }
            }
        }

        // percentiles are not aggregable
        return HistogramSnapshot.of(
                snapshots.stream().mapToLong(HistogramSnapshot::count).sum(),
                snapshots.stream().mapToDouble(HistogramSnapshot::total).sum(),
                snapshots.stream().mapToDouble(HistogramSnapshot::max).max().orElse(0.0),
                null,
                countAtBucket.entrySet().stream().map(en -> CountAtBucket.of(en.getKey(), en.getValue()))
                        .toArray(CountAtBucket[]::new));
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return timers.iterator().next().baseTimeUnit();
    }

    @Override
    public long count() {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double totalTime(TimeUnit unit) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double max(TimeUnit unit) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double percentile(double percentile, TimeUnit unit) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }

    @Override
    public <T> T record(Supplier<T> f) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }

    @Override
    public <T> T recordCallable(Callable<T> f) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }

    @Override
    public void record(Runnable f) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }
}