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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MockClock;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaBase2ExponentialHistogramTest {

    private static final int MAX_SCALE = 10;

    private MockClock clock;

    private final Duration step = Duration.ofMillis(10);

    private DeltaBase2ExponentialHistogram deltaBase2ExponentialHistogram;

    @BeforeEach
    void setUp() {
        clock = new MockClock();
        deltaBase2ExponentialHistogram = new DeltaBase2ExponentialHistogram(MAX_SCALE, 16, 1.0, null, clock,
                step.toMillis());
    }

    @Test
    void snapshotShouldBeSameForOneStep() {
        deltaBase2ExponentialHistogram.recordDouble(0.5);
        deltaBase2ExponentialHistogram.recordDouble(2.0);

        ExponentialHistogramSnapShot exponentialHistogramSnapShot = deltaBase2ExponentialHistogram
            .getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.zeroCount()).isZero();
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isZero();

        clock.add(step);
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.zeroCount()).isEqualTo(1);
        assertThat(exponentialHistogramSnapShot.scale()).isEqualTo(MAX_SCALE);
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isEqualTo(1);

        clock.add(step.dividedBy(2));
        deltaBase2ExponentialHistogram.recordDouble(4.0);
        deltaBase2ExponentialHistogram.recordDouble(1024.0);
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.zeroCount()).isEqualTo(1);
        assertThat(exponentialHistogramSnapShot.scale()).isEqualTo(MAX_SCALE);
        assertThat(exponentialHistogramSnapShot.positive().offset()).isEqualTo(1023);
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isEqualTo(1);

        clock.add(step.dividedBy(2));
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.zeroCount()).isZero();
        assertThat(exponentialHistogramSnapShot.scale()).isZero();
        assertThat(exponentialHistogramSnapShot.positive().offset()).isEqualTo(1);
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isEqualTo(2);

        clock.add(step);
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.zeroCount()).isZero();
        assertThat(exponentialHistogramSnapShot.scale()).isZero();
        assertThat(exponentialHistogramSnapShot.positive().offset()).isZero();
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isZero();

        // By this time, the histogram should be rescaled.
        assertThat(deltaBase2ExponentialHistogram.getScale()).isEqualTo(MAX_SCALE);
    }

    @Test
    void testRescalingAfterSnapshot() {
        deltaBase2ExponentialHistogram.recordDouble(1.0);
        deltaBase2ExponentialHistogram.recordDouble(2.0);
        deltaBase2ExponentialHistogram.recordDouble(1024.0);

        clock.add(step);
        ExponentialHistogramSnapShot exponentialHistogramSnapShot = deltaBase2ExponentialHistogram
            .getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.scale()).isZero();

        deltaBase2ExponentialHistogram.recordDouble(2.0);
        deltaBase2ExponentialHistogram.recordDouble(4.0);
        clock.add(step);
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.scale()).isZero();

        deltaBase2ExponentialHistogram.recordDouble(2.0);
        deltaBase2ExponentialHistogram.recordDouble(4.0);
        clock.add(step);
        exponentialHistogramSnapShot = deltaBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(exponentialHistogramSnapShot.scale()).isEqualTo(3);
    }

}
