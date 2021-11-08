/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CumulativeHistogramLongTaskTimer}.
 */
class CumulativeHistogramLongTaskTimerTest {

    MockClock clock = new MockClock();
    MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock) {
        @Override
        protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
            return new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
        }
    };

    @Test
    void histogramWithMoreBucketsThanActiveTasks() {
        LongTaskTimer ltt = LongTaskTimer.builder("my.ltt").publishPercentileHistogram().register(registry);
        ltt.start();
        clock.add(15, TimeUnit.MINUTES);
        ltt.start();
        clock.add(5, TimeUnit.MINUTES);
        // one task at 20 minutes, one task at 5 minutes
        CountAtBucket[] countAtBuckets = ltt.takeSnapshot().histogramCounts();
        int index = 0;
        while (countAtBuckets[index].bucket(TimeUnit.NANOSECONDS) < Duration.ofMinutes(5).toNanos()) {
            assertThat(countAtBuckets[index++].count()).isZero();
        }
        while (countAtBuckets[index].bucket(TimeUnit.NANOSECONDS) < Duration.ofMinutes(20).toNanos()) {
            assertThat(countAtBuckets[index++].count()).isOne();
        }
        while (index < countAtBuckets.length) {
            assertThat(countAtBuckets[index++].count()).isEqualTo(2);
        }
    }
}
