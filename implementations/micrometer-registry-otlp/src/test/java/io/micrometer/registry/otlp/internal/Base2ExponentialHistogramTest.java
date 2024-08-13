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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Base2ExponentialHistogramTest {

    private static final long MILLI_SCALE = 1000L * 1000L;

    private static final int MAX_SCALE = 10;

    private static final int MAX_BUCKETS_COUNT = 16;

    private Base2ExponentialHistogram base2ExponentialHistogram;

    @BeforeEach
    void setUp() {
        /*
         * By default, we are using 16 bucket counts since it is easy to manipulate these
         * buckets for upScaling and downscaling. Some of the facts/number to be used in
         * this test, For scale 10, base is 1.0006771306930664 index 0-15 corresponds to
         * bounds of (1.0, 1.010889286052] and Scale 0, is easier to assert things as
         * values are more human-readable.
         */

        base2ExponentialHistogram = new CumulativeBase2ExponentialHistogram(MAX_SCALE, MAX_BUCKETS_COUNT, 1.0, null);
    }

    @Test
    void testRecordDouble() {
        // 1 Always belongs to index 0.
        base2ExponentialHistogram.recordDouble(1.000000000001);
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);
        assertThat(base2ExponentialHistogram.getCurrentValuesSnapshot().zeroCount()).isZero();
        assertThat(getAllBucketsCountSum(base2ExponentialHistogram.getCurrentValuesSnapshot())).isEqualTo(1);
    }

    @Test
    void testRecordTimeBased() {
        base2ExponentialHistogram = new CumulativeBase2ExponentialHistogram(MAX_SCALE, MAX_BUCKETS_COUNT, MILLI_SCALE,
                TimeUnit.MILLISECONDS);
        base2ExponentialHistogram.recordLong(Duration.ofMillis(1).toNanos());
        base2ExponentialHistogram.recordLong(Duration.ofMillis(2).toNanos()); // This
                                                                              // should be
                                                                              // same as
                                                                              // calling
                                                                              // recordDouble(2).

        ExponentialHistogramSnapShot currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.zeroCount()).isZero();
        assertThat(currentSnapshot.scale()).isLessThan(MAX_SCALE);
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(2);
    }

    @Test
    void testRecordTimeBasedInSeconds() {
        base2ExponentialHistogram = new CumulativeBase2ExponentialHistogram(MAX_SCALE, MAX_BUCKETS_COUNT, MILLI_SCALE,
                TimeUnit.MILLISECONDS);
        base2ExponentialHistogram = new CumulativeBase2ExponentialHistogram(MAX_SCALE, MAX_BUCKETS_COUNT, MILLI_SCALE,
                TimeUnit.SECONDS);

        base2ExponentialHistogram.recordLong(Duration.ofMillis(1).toNanos());

        // This should be same as calling recordDouble(0.05).
        base2ExponentialHistogram.recordLong(Duration.ofMillis(50).toNanos());

        ExponentialHistogramSnapShot currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.zeroCount()).isZero();
        assertThat(currentSnapshot.scale()).isLessThan(MAX_SCALE);
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(2);

        base2ExponentialHistogram.recordLong(Duration.ofMillis(90).toNanos());
        currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.scale()).isEqualTo(1);
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(3);
    }

    @Test
    void testZeroThreshHold() {
        base2ExponentialHistogram.recordDouble(1.0);
        base2ExponentialHistogram.recordDouble(0.0);
        base2ExponentialHistogram.recordDouble(0.5);

        ExponentialHistogramSnapShot currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.zeroThreshold()).isLessThan(1).isGreaterThan(0);
        assertThat(currentSnapshot.zeroCount()).isEqualTo(2);
        assertThat(currentSnapshot.scale()).isEqualTo(MAX_SCALE);
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(1);

        Base2ExponentialHistogram base2ExponentialHistogramWithZeroAsMin = new CumulativeBase2ExponentialHistogram(
                MAX_SCALE, MAX_BUCKETS_COUNT, 0.0, null);
        base2ExponentialHistogramWithZeroAsMin.recordDouble(0.0);
        base2ExponentialHistogramWithZeroAsMin.recordDouble(Math.nextUp(0.0));

        ExponentialHistogramSnapShot snapshotWithZeroAsMin = base2ExponentialHistogramWithZeroAsMin
            .getCurrentValuesSnapshot();
        assertThat(snapshotWithZeroAsMin.zeroThreshold()).isEqualTo(0.0);
        assertThat(snapshotWithZeroAsMin.zeroCount()).isEqualTo(1);
        assertThat(snapshotWithZeroAsMin.scale()).isEqualTo(MAX_SCALE);
        assertThat(getAllBucketsCountSum(snapshotWithZeroAsMin)).isEqualTo(1);
    }

    @Test
    void testDownScale() {
        base2ExponentialHistogram.recordDouble(1.0001);

        ExponentialHistogramSnapShot currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.zeroCount()).isZero();
        assertThat(currentSnapshot.scale()).isEqualTo(MAX_SCALE);
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(1);

        base2ExponentialHistogram.recordDouble(1.011);
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE - 1);

        base2ExponentialHistogram.recordDouble(512);
        assertThat(base2ExponentialHistogram.getScale()).isZero();

        base2ExponentialHistogram.recordDouble(65537);
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(-1);
    }

    @Test
    void testUpscale() {
        base2ExponentialHistogram.recordDouble(1.0001);
        base2ExponentialHistogram.recordDouble(512); // Scale is 0 now.

        base2ExponentialHistogram.reset();
        assertThat(base2ExponentialHistogram.getScale()).isZero();

        base2ExponentialHistogram.recordDouble(1.0001);
        base2ExponentialHistogram.reset();
        // When there is only one recording we expect the scale to be reset to maxScale.
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);

        base2ExponentialHistogram.recordDouble(1.0001);
        base2ExponentialHistogram.recordDouble(512);
        base2ExponentialHistogram.reset();

        // We will still be recording in higher scale, i.e 0.
        base2ExponentialHistogram.recordDouble(1.0001);
        base2ExponentialHistogram.recordDouble(4);
        assertThat(base2ExponentialHistogram.getScale()).isZero();

        // Now 1-4 uses only 3 buckets in scale 0 and the best scale to record values
        // under 4 with 16 buckets will be 3.
        base2ExponentialHistogram.reset();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(3);

        base2ExponentialHistogram.recordDouble(1.0001);
        base2ExponentialHistogram.recordDouble(2);

        // Now (1-2] uses only 8 buckets in scale 3 and the best scale to record values
        // between (1,2] with 16 buckets
        // will be 4.
        base2ExponentialHistogram.reset();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(4);

        base2ExponentialHistogram.reset();
        // When no values are recorded, we MUST fall back to maximum scale.
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);
    }

    @Test
    void testValuesAtIndices() {
        ExponentialHistogramSnapShot currentValueSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentValueSnapshot.positive().bucketCounts()).isEmpty();

        base2ExponentialHistogram.recordDouble(1.0001);
        currentValueSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentValueSnapshot.positive().offset()).isZero();
        assertThat(currentValueSnapshot.positive().bucketCounts().get(0)).isEqualTo(1);
        assertThat(currentValueSnapshot.positive().bucketCounts()).filteredOn(value -> value == 0).isEmpty();

        base2ExponentialHistogram.recordDouble(1.0008);

        base2ExponentialHistogram.recordDouble(1.0076);
        base2ExponentialHistogram.recordDouble(1.008);
        currentValueSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentValueSnapshot.positive().offset()).isZero();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(0)).isEqualTo(1);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(1)).isEqualTo(1);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(11)).isEqualTo(2);
        assertThat(currentValueSnapshot.positive().bucketCounts()).filteredOn(value -> value == 0).hasSize(9);

        // We will record a value that will downscale by 1 and this should merge 2
        // consecutive buckets into one.
        base2ExponentialHistogram.recordDouble(1.012);
        currentValueSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentValueSnapshot.positive().offset()).isZero();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE - 1);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(0)).isEqualTo(2);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(5)).isEqualTo(2);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(8)).isEqualTo(1);
        assertThat(currentValueSnapshot.positive().bucketCounts()).filteredOn(value -> value == 0).hasSize(6);

        // The base will reduced by a factor of more than one,
        base2ExponentialHistogram.recordDouble(4);
        currentValueSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentValueSnapshot.positive().offset()).isZero();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(3);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(0)).isEqualTo(5);
        assertThat(currentValueSnapshot.positive().bucketCounts().get(15)).isEqualTo(1);
        assertThat(currentValueSnapshot.positive().bucketCounts()).filteredOn(value -> value == 0).hasSize(14);
    }

    @Test
    void testUpscaleForNegativeScale() {
        base2ExponentialHistogram.recordDouble(2);
        base2ExponentialHistogram.recordDouble(65537);
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(-1);
        base2ExponentialHistogram.reset();

        base2ExponentialHistogram.recordDouble(2);
        base2ExponentialHistogram.reset();
        assertThat(base2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);
    }

    @Test
    void reset() {
        base2ExponentialHistogram.recordDouble(Math.nextDown(1.0));
        base2ExponentialHistogram.recordDouble(1);
        base2ExponentialHistogram.recordDouble(2);

        ExponentialHistogramSnapShot currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        final int intialScale = currentSnapshot.scale();
        assertThat(currentSnapshot.zeroCount()).isEqualTo(1);
        assertThat(currentSnapshot.scale()).isLessThan(MAX_SCALE);
        assertThat(currentSnapshot.positive().offset()).isNotZero();
        assertThat(getAllBucketsCountSum(currentSnapshot)).isEqualTo(2);

        base2ExponentialHistogram.reset();
        currentSnapshot = base2ExponentialHistogram.getCurrentValuesSnapshot();
        assertThat(currentSnapshot.zeroCount()).isZero();
        assertThat(currentSnapshot.scale()).isEqualTo(intialScale);
        assertThat(currentSnapshot.positive().offset()).isZero();
        assertThat(getAllBucketsCountSum(currentSnapshot)).isZero();
    }

    static long getAllBucketsCountSum(ExponentialHistogramSnapShot snapShot) {
        return snapShot.positive().bucketCounts().stream().mapToLong(Long::longValue).sum();
    }

}
