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
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class AggregateDistributionSummary extends AggregateMeter implements DistributionSummary {
    private final Collection<DistributionSummary> summaries;

    AggregateDistributionSummary(Id aggregateId, Collection<DistributionSummary> summaries) {
        super(aggregateId);
        this.summaries = summaries;
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        Collection<HistogramSnapshot> snapshots = summaries.stream()
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

    public long count() {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double totalAmount() {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double max() {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double percentile(double percentile) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public double histogramCountAtValue(long valueNanos) {
        throw new UnsupportedOperationException("Reporting should use results of takeSnapshot instead");
    }

    @Override
    public void record(double amount) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }
}