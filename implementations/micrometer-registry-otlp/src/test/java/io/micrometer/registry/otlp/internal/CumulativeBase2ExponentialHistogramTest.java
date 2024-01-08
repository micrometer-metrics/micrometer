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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CumulativeBase2ExponentialHistogramTest {

    private static final int MAX_SCALE = 10;

    private CumulativeBase2ExponentialHistogram cumulativeBase2ExponentialHistogram;

    @BeforeEach
    void setUp() {
        cumulativeBase2ExponentialHistogram = new CumulativeBase2ExponentialHistogram(MAX_SCALE, 16, 1.0, null);
    }

    @Test
    void testDataIsAccumulatedCumulatively() {
        cumulativeBase2ExponentialHistogram.recordDouble(2.0);
        cumulativeBase2ExponentialHistogram.recordDouble(2.1);

        cumulativeBase2ExponentialHistogram.takeSnapshot(0, 0, 0);
        ExponentialHistogramSnapShot exponentialHistogramSnapShot = cumulativeBase2ExponentialHistogram
            .getLatestExponentialHistogramSnapshot();

        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isEqualTo(2);
        assertThat(exponentialHistogramSnapShot.scale()).isEqualTo(7);

        cumulativeBase2ExponentialHistogram.recordDouble(4);
        cumulativeBase2ExponentialHistogram.takeSnapshot(0, 0, 0);
        exponentialHistogramSnapShot = cumulativeBase2ExponentialHistogram.getLatestExponentialHistogramSnapshot();
        assertThat(Base2ExponentialHistogramTest.getAllBucketsCountSum(exponentialHistogramSnapShot)).isEqualTo(3);
        assertThat(exponentialHistogramSnapShot.scale()).isEqualTo(3);
    }

}
