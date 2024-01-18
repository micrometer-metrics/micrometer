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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class DefaultLongTaskTimer extends AbstractMeter implements LongTaskTimer {

    /**
     * Preferring {@link ConcurrentLinkedDeque} over {@link CopyOnWriteArrayList} here
     * because...
     * <p>
     * Retrieval of percentile values will be O(N) but starting/stopping tasks will be
     * O(1). Starting/stopping tasks happen in the same thread as the main application
     * code, where publishing generally happens in a separate thread. Also, shipping
     * client-side percentiles should be relatively uncommon.
     * <p>
     * Histogram creation is O(N) for both the queue and list options, because we have to
     * consider which bucket each active task belongs.
     */
    private final Deque<SampleImpl> activeTasks = new ConcurrentLinkedDeque<>();

    private final Clock clock;

    private final TimeUnit baseTimeUnit;

    private final DistributionStatisticConfig distributionStatisticConfig;

    private final boolean supportsAggregablePercentiles;

    private final double[] percentiles;

    /**
     * Create a {@code DefaultLongTaskTimer} instance.
     * @param id ID
     * @param clock clock
     * @deprecated Use
     * {@link #DefaultLongTaskTimer(Meter.Id, Clock, TimeUnit, DistributionStatisticConfig, boolean)}
     * instead.
     */
    @Deprecated
    public DefaultLongTaskTimer(Id id, Clock clock) {
        this(id, clock, TimeUnit.MILLISECONDS, DistributionStatisticConfig.DEFAULT, false);
    }

    /**
     * Create a {@code DefaultLongTaskTimer} instance.
     * @param id ID
     * @param clock clock
     * @param baseTimeUnit base time unit
     * @param distributionStatisticConfig distribution statistic configuration
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @since 1.5.0
     */
    public DefaultLongTaskTimer(Id id, Clock clock, TimeUnit baseTimeUnit,
            DistributionStatisticConfig distributionStatisticConfig, boolean supportsAggregablePercentiles) {
        super(id);
        this.clock = clock;
        this.baseTimeUnit = baseTimeUnit;
        this.distributionStatisticConfig = distributionStatisticConfig;
        this.supportsAggregablePercentiles = supportsAggregablePercentiles;

        double[] percentilesFromConfig = distributionStatisticConfig.getPercentiles();

        if (percentilesFromConfig == null) {
            this.percentiles = new double[0];
        }
        else {
            this.percentiles = new double[percentilesFromConfig.length];
            System.arraycopy(percentilesFromConfig, 0, this.percentiles, 0, percentilesFromConfig.length);
            Arrays.sort(this.percentiles);
        }
    }

    @Override
    public Sample start() {
        SampleImpl sample = new SampleImpl();
        activeTasks.add(sample);
        return sample;
    }

    @Override
    public double duration(TimeUnit unit) {
        long now = clock.monotonicTime();
        long sum = 0L;
        for (SampleImpl task : activeTasks) {
            sum += now - task.startTime();
        }
        return TimeUtils.nanosToUnit(sum, unit);
    }

    @Override
    public double max(TimeUnit unit) {
        Sample oldest = activeTasks.peek();
        return oldest == null ? 0.0 : oldest.duration(unit);
    }

    @Override
    public int activeTasks() {
        return activeTasks.size();
    }

    protected void forEachActive(Consumer<Sample> sample) {
        activeTasks.forEach(sample);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }

    private int percentilesInterpolatableLine(int activeTasksNumber) {
        double lineValue = activeTasksNumber * 1.0 / (activeTasksNumber + 1);
        for (int i = percentiles.length - 1; i >= 0; --i) {
            if (percentiles[i] < lineValue) {
                return i + 1;
            }
        }

        return 0;
    }

    private Snapshot activeTaskDurationsSnapshot() {
        Snapshot snapshot = new Snapshot(activeTasks.size() + 1);

        StreamSupport.stream(((Iterable<SampleImpl>) activeTasks::descendingIterator).spliterator(), false)
            .sequential()
            .forEach(task -> {
                double duration = task.duration(TimeUnit.NANOSECONDS);
                if (duration < 0) {
                    return;
                }
                snapshot.append(duration);
            });

        return snapshot;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        Snapshot snapshot = activeTaskDurationsSnapshot();

        NavigableSet<Double> buckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);

        CountAtBucket[] countAtBucketsArr = new CountAtBucket[0];

        int percentilesInterpolatableLine = percentilesInterpolatableLine(snapshot.size());

        ValueAtPercentile[] valueAtPercentiles = new ValueAtPercentile[percentiles.length];

        if (percentilesInterpolatableLine > 0 || !buckets.isEmpty()) {
            int currentPercentileIdx = 0;
            Double bucket = buckets.pollFirst();

            List<CountAtBucket> countAtBuckets = new ArrayList<>(buckets.size());

            double priorActiveTaskDuration = -1;
            int count = 0;

            double[] youngestToOldestDurations = snapshot.durations();
            for (int currentTaskDurationIdx = 0; currentTaskDurationIdx < snapshot.size(); ++currentTaskDurationIdx) {
                double activeTaskDuration = youngestToOldestDurations[currentTaskDurationIdx];

                while (bucket != null && activeTaskDuration > bucket) {
                    countAtBuckets.add(new CountAtBucket(bucket, count));
                    bucket = buckets.pollFirst();
                }
                count++;

                if (currentPercentileIdx < percentilesInterpolatableLine) {
                    double percentile = percentiles[currentPercentileIdx];
                    double rank = percentile * (snapshot.size() + 1);

                    if (count >= rank) {
                        double percentileValue = activeTaskDuration;
                        if (count != rank && priorActiveTaskDuration >= 0) {
                            // interpolate the percentile value when the active task rank
                            // is non-integral
                            double priorPercentileValue = priorActiveTaskDuration;
                            percentileValue = priorPercentileValue
                                    + ((percentileValue - priorPercentileValue) * (rank - (int) rank));
                        }

                        valueAtPercentiles[currentPercentileIdx] = new ValueAtPercentile(percentile, percentileValue);
                        ++currentPercentileIdx;
                    }
                }

                priorActiveTaskDuration = activeTaskDuration;
            }

            // fill out the rest of the cumulative histogram
            while (bucket != null) {
                countAtBuckets.add(new CountAtBucket(bucket, count));
                bucket = buckets.pollFirst();
            }

            countAtBucketsArr = countAtBuckets.toArray(countAtBucketsArr);
        }

        double duration = snapshot.total();
        double max = snapshot.max();

        // we wouldn't need to iterate over all the active tasks just to calculate the
        // 100th percentile, which is just the max.
        for (int currentPercentileIdx = percentilesInterpolatableLine; currentPercentileIdx < percentiles.length; ++currentPercentileIdx) {
            valueAtPercentiles[currentPercentileIdx] = new ValueAtPercentile(percentiles[currentPercentileIdx], max);
        }

        return new HistogramSnapshot(activeTasks.size(), duration, max, valueAtPercentiles, countAtBucketsArr,
                (ps, scaling) -> ps.print("Summary output for LongTaskTimer histograms is not supported."));
    }

    static final class Snapshot {

        private double[] durations;

        private int size;

        private double max;

        private double total;

        Snapshot(int initialCapacity) {
            this.size = 0;
            this.durations = new double[initialCapacity];
            this.max = 0.0;
            this.total = 0.0;
        }

        public void append(double duration) {
            if (size >= durations.length) {
                double[] newDurations = new double[(durations.length + 1) * 2];
                System.arraycopy(durations, 0, newDurations, 0, durations.length);
                durations = newDurations;
            }

            if (duration > max) {
                max = duration;
            }
            total += duration;

            durations[size] = duration;
            ++size;
        }

        public double[] durations() {
            return durations;
        }

        public int size() {
            return size;
        }

        public double max() {
            return max;
        }

        public double total() {
            return total;
        }

    }

    class SampleImpl extends Sample {

        private final long startTime;

        private volatile boolean stopped;

        private SampleImpl() {
            this.startTime = clock.monotonicTime();
        }

        @Override
        public long stop() {
            activeTasks.remove(this);
            long duration = (long) duration(TimeUnit.NANOSECONDS);
            stopped = true;
            return duration;
        }

        @Override
        public double duration(TimeUnit unit) {
            return stopped ? -1 : TimeUtils.nanosToUnit(clock.monotonicTime() - startTime, unit);
        }

        private long startTime() {
            return startTime;
        }

        @Override
        public String toString() {
            double durationInNanoseconds = duration(TimeUnit.NANOSECONDS);
            return "SampleImpl{" + "duration(seconds)=" + TimeUtils.nanosToUnit(durationInNanoseconds, TimeUnit.SECONDS)
                    + ", " + "duration(nanos)=" + durationInNanoseconds + ", " + "startTimeNanos=" + startTime + '}';
        }

    }

}
