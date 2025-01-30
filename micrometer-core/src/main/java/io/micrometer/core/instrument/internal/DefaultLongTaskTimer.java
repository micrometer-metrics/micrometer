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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DefaultLongTaskTimer extends AbstractMeter implements LongTaskTimer {

    /**
     * Preferring {@link ConcurrentSkipListSet} over other concurrent collections
     * because...
     * <p>
     * Retrieval of percentile values will be O(N) but starting/stopping tasks will be
     * O(log(N)). Starting/stopping tasks happen in the same thread as the main
     * application code, where publishing generally happens in a separate thread. Also,
     * shipping client-side percentiles should be relatively uncommon.
     * <p>
     * Tasks are naturally ordered at time of insertion, but task completion/removal can
     * happen out-of-order, that causes removal of tasks from the middle of the list. A
     * queue would provide O(1) insertion, but O(N) removal in average. A skip-list
     * provides O(log(N)) average across all tasks.
     * <p>
     * Histogram creation is O(N) for both the queue and list options, because we have to
     * consider which bucket each active task belongs.
     */
    private final NavigableSet<SampleImpl> activeTasks = new ConcurrentSkipListSet<>();

    private final AtomicInteger counter = new AtomicInteger();

    private final Clock clock;

    private final TimeUnit baseTimeUnit;

    private final DistributionStatisticConfig distributionStatisticConfig;

    private final boolean supportsAggregablePercentiles;

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
    }

    @Override
    public Sample start() {
        long startTime = clock.monotonicTime();
        SampleImpl sample = new SampleImpl(startTime);
        if (!activeTasks.add(sample)) {
            sample = new SampleImplCounted(startTime, nextNonZeroCounter());
            activeTasks.add(sample);
        }
        return sample;
    }

    private int nextNonZeroCounter() {
        int nextCount;
        while ((nextCount = counter.incrementAndGet()) == 0) {
        }
        return nextCount;
    }

    // @VisibleForTesting
    void setCounter(int newCounter) {
        counter.set(newCounter);
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
        try {
            return activeTasks.first().duration(unit);
        }
        catch (NoSuchElementException e) {
            return 0.0;
        }
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

    @Override
    public HistogramSnapshot takeSnapshot() {
        double[] percentilesRequestedArr = distributionStatisticConfig.getPercentiles();
        Queue<Double> percentilesRequested = new ArrayBlockingQueue<>(
                percentilesRequestedArr == null || percentilesRequestedArr.length == 0 ? 1
                        : distributionStatisticConfig.getPercentiles().length);
        if (percentilesRequestedArr != null && percentilesRequestedArr.length > 0) {
            Arrays.stream(percentilesRequestedArr).sorted().boxed().forEach(percentilesRequested::add);
        }

        NavigableSet<Double> buckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);

        CountAtBucket[] countAtBucketsArr = new CountAtBucket[0];

        List<Double> percentilesAboveInterpolatableLine = percentilesRequested.stream()
            .filter(p -> p * (activeTasks.size() + 1) > activeTasks.size())
            .collect(Collectors.toList());

        percentilesRequested.removeAll(percentilesAboveInterpolatableLine);

        List<ValueAtPercentile> valueAtPercentiles = new ArrayList<>(percentilesRequested.size());

        if (!percentilesRequested.isEmpty() || !buckets.isEmpty()) {
            Double percentile = percentilesRequested.poll();
            Double bucket = buckets.pollFirst();

            List<CountAtBucket> countAtBuckets = new ArrayList<>(buckets.size());

            Double priorActiveTaskDuration = null;
            int count = 0;

            // Make snapshot of active task durations
            List<Double> youngestToOldestDurations = StreamSupport
                .stream(((Iterable<SampleImpl>) activeTasks::descendingIterator).spliterator(), false)
                .sequential()
                .map(task -> task.duration(TimeUnit.NANOSECONDS))
                .collect(Collectors.toList());
            for (Double activeTaskDuration : youngestToOldestDurations) {
                while (bucket != null && activeTaskDuration > bucket) {
                    countAtBuckets.add(new CountAtBucket(bucket, count));
                    bucket = buckets.pollFirst();
                }
                count++;

                if (percentile != null) {
                    double rank = percentile * (activeTasks.size() + 1);

                    if (count >= rank) {
                        double percentileValue = activeTaskDuration;
                        if (count != rank && priorActiveTaskDuration != null) {
                            // interpolate the percentile value when the active task rank
                            // is non-integral
                            double priorPercentileValue = priorActiveTaskDuration;
                            percentileValue = priorPercentileValue
                                    + ((percentileValue - priorPercentileValue) * (rank - (int) rank));
                        }

                        valueAtPercentiles.add(new ValueAtPercentile(percentile, percentileValue));
                        percentile = percentilesRequested.poll();
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

        double duration = duration(TimeUnit.NANOSECONDS);
        double max = max(TimeUnit.NANOSECONDS);

        // we wouldn't need to iterate over all the active tasks just to calculate the
        // 100th percentile, which is just the max.
        for (Double percentile : percentilesAboveInterpolatableLine) {
            valueAtPercentiles.add(new ValueAtPercentile(percentile, max));
        }

        ValueAtPercentile[] valueAtPercentilesArr = valueAtPercentiles.toArray(new ValueAtPercentile[0]);

        return new HistogramSnapshot(activeTasks.size(), duration, max, valueAtPercentilesArr, countAtBucketsArr,
                (ps, scaling) -> ps.print("Summary output for LongTaskTimer histograms is not supported."));
    }

    class SampleImpl extends Sample implements Comparable<SampleImpl> {

        private final long startTime;

        private volatile boolean stopped;

        private SampleImpl(long startTime) {
            this.startTime = startTime;
        }

        int counter() {
            return 0;
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
                    + ", duration(nanos)=" + durationInNanoseconds + ", startTimeNanos=" + startTime + '}';
        }

        @Override
        public int compareTo(DefaultLongTaskTimer.SampleImpl that) {
            if (this == that) {
                return 0;
            }
            int startCompare = Long.compare(this.startTime, that.startTime);
            if (startCompare == 0) {
                return Integer.compare(this.counter(), that.counter());
            }
            return startCompare;
        }

    }

    class SampleImplCounted extends SampleImpl {

        private final int counter;

        private SampleImplCounted(long startTime, int counter) {
            super(startTime);
            this.counter = counter;
        }

        @Override
        int counter() {
            return counter;
        }

        @Override
        public String toString() {
            double durationInNanoseconds = duration(TimeUnit.NANOSECONDS);
            return "SampleImplCounted{" + "duration(seconds)="
                    + TimeUtils.nanosToUnit(durationInNanoseconds, TimeUnit.SECONDS) + ", duration(nanos)="
                    + durationInNanoseconds + ", startTimeNanos=" + super.startTime + ", counter=" + this.counter + '}';
        }

    }

}
