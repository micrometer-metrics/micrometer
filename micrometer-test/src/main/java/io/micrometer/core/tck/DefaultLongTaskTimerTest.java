/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;

// Test class is public and in main code... by mistake?
//CHECKSTYLE:OFF
public class DefaultLongTaskTimerTest {

    // CHECKSTYLE:ON

    @Test
    @DisplayName("supports sending histograms of active task duration")
    void histogram() {
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        LongTaskTimer t = LongTaskTimer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofSeconds(10), Duration.ofSeconds(40), Duration.ofMinutes(1))
            .register(registry);

        List<Integer> samples = Arrays.asList(48, 42, 40, 35, 22, 16, 13, 8, 6, 4, 2);
        int prior = samples.get(0);
        for (Integer value : samples) {
            clock(registry).add(prior - value, TimeUnit.SECONDS);
            t.start();
            prior = value;
        }
        clock(registry).add(samples.get(samples.size() - 1), TimeUnit.SECONDS);

        CountAtBucket[] countAtBuckets = t.takeSnapshot().histogramCounts();

        assertThat(countAtBuckets[0].bucket(TimeUnit.SECONDS)).isEqualTo(10);
        assertThat(countAtBuckets[0].count()).isEqualTo(4);

        assertThat(countAtBuckets[1].bucket(TimeUnit.SECONDS)).isEqualTo(40);
        assertThat(countAtBuckets[1].count()).isEqualTo(9);

        assertThat(countAtBuckets[2].bucket(TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(countAtBuckets[2].count()).isEqualTo(11);
    }

    @Test
    void histogramWithMoreBucketsThanActiveTasks() {
        MockClock clock = new MockClock();
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock) {
            @Override
            protected LongTaskTimer newLongTaskTimer(Meter.Id id,
                    DistributionStatisticConfig distributionStatisticConfig) {
                // supportsAggregablePercentiles true for using pre-defined histogram
                // buckets
                return new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, true);
            }
        };
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
