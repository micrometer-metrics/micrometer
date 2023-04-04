/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.dynatrace.types;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests for {@link DynatraceSummary}.
 *
 * @author Georg Pirklbauer
 */
class DynatraceSummaryTest {

    private static final Offset<Double> OFFSET = Offset.offset(0.0001);

    @Test
    void testRecordValues() {
        DynatraceSummary summary = new DynatraceSummary();
        summary.recordNonNegative(3.14);
        summary.recordNonNegative(4.76);

        assertMinMaxSumCount(summary, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testRecordNegativeIgnored() {
        DynatraceSummary summary = new DynatraceSummary();
        summary.recordNonNegative(3.14);
        summary.recordNonNegative(-1.234);
        summary.recordNonNegative(4.76);
        summary.recordNonNegative(-6.789);

        assertMinMaxSumCount(summary, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testReset() {
        DynatraceSummary summary = new DynatraceSummary();
        summary.recordNonNegative(3.14);
        summary.recordNonNegative(4.76);

        assertMinMaxSumCount(summary, 3.14, 4.76, 7.9, 2);
        summary.reset();
        assertMinMaxSumCount(summary, 0d, 0d, 0d, 0);
    }

    @Test
    void testMinMaxAreOverwritten() {
        DynatraceSummary summary = new DynatraceSummary();
        summary.recordNonNegative(3.14);
        summary.recordNonNegative(4.76);
        summary.recordNonNegative(0.123);
        summary.recordNonNegative(8.93);

        assertMinMaxSumCount(summary, 0.123, 8.93, 16.953, 4);
    }

    @Test
    void testConcurrentAdds() throws InterruptedException {
        DynatraceSummary summary = new DynatraceSummary();

        int valueRecordedNTimes = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        double expMin = 1.234;
        double expMax = 123.456;
        double expAvg = (expMin + expMax) / 2;

        // first executor adds the min 100 times
        executorService.submit(() -> {
            for (int i = 0; i < valueRecordedNTimes; i++) {
                summary.recordNonNegative(expMin);
            }
        });

        // second executor records the avg 100 times
        executorService.submit(() -> {
            for (int i = 0; i < valueRecordedNTimes; i++) {
                summary.recordNonNegative(expAvg);
            }
        });

        // third executor records the max 100 times
        executorService.submit(() -> {
            for (int i = 0; i < valueRecordedNTimes; i++) {
                summary.recordNonNegative(expMax);
            }
        });

        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        assertThat(terminated).isTrue();

        double expTotal = (valueRecordedNTimes * expMin) + (valueRecordedNTimes * expAvg)
                + (valueRecordedNTimes * expMax);
        assertMinMaxSumCount(summary, expMin, expMax, expTotal, 300);
    }

    private void assertMinMaxSumCount(DynatraceSummary summary, Double expMin, Double expMax, Double expTotal,
            long expCount) {
        assertThat(summary.getMin()).isCloseTo(expMin, OFFSET);
        assertThat(summary.getMax()).isCloseTo(expMax, OFFSET);
        assertThat(summary.getCount()).isEqualTo(expCount);
        assertThat(summary.getTotal()).isCloseTo(expTotal, OFFSET);
    }

}
