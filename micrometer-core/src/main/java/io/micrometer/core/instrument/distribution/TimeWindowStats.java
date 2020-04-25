/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import io.micrometer.core.instrument.Clock;

/**
 * An implementation of a moving-window stats based on a configurable ring buffer.
 * Currently exposed statistics are :
 * <ul>
 * <li>Samples sum</li>
 * <li>Samples count</li>
 * <li>Samples mean</li>
 * <li>Samples min</li>
 * <li>Samples frequency (events / milliseconds)</li>
 * <li>Time window's oldest event's age</li>
 * </ul>
 *
 * @author N.Billard
 */
public class TimeWindowStats {
    private static final long MAX_DOUBLE_TO_LONG = 0x7ff0000000000000L - 1;
    
    private static final AtomicIntegerFieldUpdater<TimeWindowStats> rotatingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(TimeWindowStats.class, "rotating");

    private final Clock clock;
    private final long durationBetweenRotatesMillis;
    private final AtomicHolder[] ringBuffer;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int rotating = 0; // 0 - not rotating, 1 - rotating
    
    private final TimeWindowStatsSnapshot snapshot = new TimeWindowStatsSnapshot() {
        @Override
        public double sum() {
            return TimeWindowStats.this.sum();
        }
        @Override
        public double min() {
            return TimeWindowStats.this.min();
        }
        @Override
        public double max() {
            return TimeWindowStats.this.max();
        }
        @Override
        public double mean() {
            return TimeWindowStats.this.mean();
        }
        @Override
        public long count() {
            return TimeWindowStats.this.count();
        }
        @Override
        public double freq() {
            return TimeWindowStats.this.freq();
        }
        @Override
        public long age() {
            return TimeWindowStats.this.age();
        }
    };

    public TimeWindowStats(final Clock clock, final DistributionStatisticConfig config) {
        this(clock, config.getExpiry().toMillis(), config.getBufferLength());
    }

    public TimeWindowStats(final Clock clock, final long rotateFrequencyMillis, final int bufferLength) {
        this.clock = clock;
        durationBetweenRotatesMillis = rotateFrequencyMillis;
        lastRotateTimestampMillis = clock.wallTime();
        currentBucket = 0;

        ringBuffer = new AtomicHolder[bufferLength];
        for (int i = 0; i < bufferLength; i++) {
            ringBuffer[i] = new AtomicHolder();
        }
        ringBuffer[currentBucket].age = lastRotateTimestampMillis;
    }

    private static class AtomicHolder {
        long age = 0L;
        LongAdder cnt = new LongAdder();
        DoubleAdder sum = new DoubleAdder();
        AtomicLong min = new AtomicLong(MAX_DOUBLE_TO_LONG);
        AtomicLong max = new AtomicLong();

        @Override
        public String toString() {
            return "AtomicHolder [age=" + age + ", cnt=" + cnt + ", sum=" + sum + ", min=" + min + ", max=" + max + "]";
        }
    }

    /**
     * Increments counter.
     * @param sample Sample to record
     */
    public void record(final double sample) {
        if (sample >= 0) {
            rotate();
            //No need to synchronize here
            //Don't use updateAndGet exposed by updateAndGet : performances are awful.
            long doubleToLongBits = Double.doubleToLongBits(sample);
            final AtomicHolder current = ringBuffer[currentBucket];
            current.cnt.add(1);
            current.sum.add(sample);
			updateMin(current.min, doubleToLongBits);
            updateMax(current.max, doubleToLongBits);
        }
    }

    private void updateMin(AtomicLong min, long sample) {
        for (; ; ) {
            long curMin = min.get();
            if (curMin <= sample || min.compareAndSet(curMin, sample))
                break;
        }
    }

    private void updateMax(AtomicLong max, long sample) {
        for (; ; ) {
            long curMax = max.get();
            if (curMax >= sample || max.compareAndSet(curMax, sample))
                break;
        }
    }

    /**
     * @return event count on time window.
     */
    public long count() {
        rotate();
        long cnt = 0L;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                if (element.age > 0) {
                    cnt += element.cnt.sum();
                }
            }
        }
        return cnt;
    }

    /**
     * @return sum on time window.
     */
    public double sum() {
        rotate();
        double sum = 0L;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                if (element.age > 0) {
                    sum += element.sum.sum();
                }
            }
        }
        return sum;
    }

    /**
     * @return min on time window.
     */
    public double min() {
        rotate();
        long min = MAX_DOUBLE_TO_LONG;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                if (element.age > 0) {
                    min = Math.min(min, element.min.get());
                }
            }
        }
        return Double.longBitsToDouble(min);
    }

    /**
     * @return max on time window.
     */
    public double max() {
        rotate();
        long max = 0;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                if (element.age > 0) {
                    max = Math.max(max, element.max.get());
                }
            }
        }
        return Double.longBitsToDouble(max);
    }

    /**
     * @return mean on time window.
     */
    public double mean() {
        rotate();
        double sum = 0L;
        long cnt = 0L;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                if (element.age > 0) {
                    sum += element.sum.sum();
                    cnt += element.cnt.sum();
                }
            }
        }
        return sum / cnt;
    }

    /**
     * @return frequency (events / milliseconds) on time window.
     */
    public double freq() {
        rotate();
        long minAge = Long.MAX_VALUE;
        long cnt = 0L;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                final long age = element.age;
                if (age > 0) {
                    minAge = Long.min(minAge, age);
                    cnt += element.cnt.sum();
                }
            }
        }
        return (double) cnt / (clock.wallTime() - minAge);
    }

    /**
     * @return age of the oldest time window's sample in milliseconds.
     */
    public long age() {
        rotate();
        long minAge = Long.MAX_VALUE;
        synchronized (this) {
            for (final AtomicHolder element : ringBuffer) {
                final long age = element.age;
                if (age > 0) {
                    minAge = Long.min(minAge, age);
                }
            }
        }
        return clock.wallTime() - minAge;
    }

    /**
     * @return Unmodifiable Snapshot.
     */
    public TimeWindowStatsSnapshot getSnapshot() {
        return snapshot;
    }

    private void rotate() {
        final long wallTime = clock.wallTime();
        long timeSinceLastRotateMillis = wallTime - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }

        if (!TimeWindowStats.rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }

        try {
            int iterations = 0;
            synchronized (this) {
                do {
                	int tmpCurrentBucket = currentBucket + 1;
                    if (tmpCurrentBucket >= ringBuffer.length) {
                        currentBucket = 0;
                    } else {
                    	currentBucket = tmpCurrentBucket;
                    }

                    //Init old buffers
                    if (ringBuffer[currentBucket].age > 0L) {
	                    ringBuffer[currentBucket].age = 0L;
	                    ringBuffer[currentBucket].min.set(MAX_DOUBLE_TO_LONG);
	                    ringBuffer[currentBucket].max.set(0L);
	                    ringBuffer[currentBucket].sum.reset();
	                    ringBuffer[currentBucket].cnt.reset();
                    }

                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
                } while (timeSinceLastRotateMillis >= durationBetweenRotatesMillis && ++iterations < ringBuffer.length);

                //New buffer starts now
                ringBuffer[currentBucket].age = wallTime;
            }
        } finally {
            rotating = 0;
        }
    }

    /**
     * Provides a way to expose a readonly snapshot of stats.
     */
    public interface TimeWindowStatsSnapshot {
        /**
         * @return event count on time window.
         */
        public long count();

        /**
         * @return sum on time window.
         */
        public double sum();

        /**
         * @return min on time window.
         */
        public double min();

        /**
         * @return max on time window.
         */
        public double max();

        /**
         * @return mean on time window.
         */
        public double mean();

        /**
         * @return frequency (events / milliseconds) on time window.
         */
        public double freq();

        /**
         * @return age of the oldest time window's sample in milliseconds.
         */
        public long age();
    }
}