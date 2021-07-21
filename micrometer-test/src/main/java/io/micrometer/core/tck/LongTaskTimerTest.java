/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

interface LongTaskTimerTest {

    @Test
    @DisplayName("total time is preserved for a single timing")
    default void record(MeterRegistry registry) {
        LongTaskTimer t = registry.more().longTaskTimer("my.timer");

        LongTaskTimer.Sample sample = t.start();
        clock(registry).add(10, TimeUnit.NANOSECONDS);

        assertAll(() -> assertEquals(10, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, t.duration(TimeUnit.MICROSECONDS)),
            () -> assertEquals(10, sample.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, sample.duration(TimeUnit.MICROSECONDS)),
            () -> assertEquals(1, t.activeTasks()));

        clock(registry).add(10, TimeUnit.NANOSECONDS);
        sample.stop();

        assertAll(() -> assertEquals(0, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(-1, sample.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0, t.activeTasks()));
    }

    @Test
    @DisplayName("supports sending the Nth percentile active task duration")
    default void percentiles(MeterRegistry registry) {
        LongTaskTimer t = LongTaskTimer.builder("my.timer")
                .publishPercentiles(0.5, 0.7, 0.91, 0.999, 1)
                .register(registry);

        // Using the example of percentile interpolation from https://statisticsbyjim.com/basics/percentiles/
        List<Integer> samples = Arrays.asList(48, 42, 40, 35, 22, 16, 13, 8, 6, 4, 2);
        int prior = samples.get(0);
        for (Integer value : samples) {
            clock(registry).add(prior - value, TimeUnit.SECONDS);
            t.start();
            prior = value;
        }
        clock(registry).add(samples.get(samples.size() - 1), TimeUnit.SECONDS);

        assertThat(t.activeTasks()).isEqualTo(11);

        ValueAtPercentile[] percentiles = t.takeSnapshot().percentileValues();

        assertThat(percentiles[0].percentile()).isEqualTo(0.5);
        assertThat(percentiles[0].value(TimeUnit.SECONDS)).isEqualTo(16);

        assertThat(percentiles[1].percentile()).isEqualTo(0.7);
        assertThat(percentiles[1].value(TimeUnit.SECONDS)).isEqualTo(37, within(0.001));

        // a value close-to the highest value that is available for interpolation (11 / 12)
        assertThat(percentiles[2].percentile()).isEqualTo(0.91);
        assertThat(percentiles[2].value(TimeUnit.SECONDS)).isEqualTo(47.5, within(0.1));

        assertThat(percentiles[3].percentile()).isEqualTo(0.999);
        assertThat(percentiles[3].value(TimeUnit.SECONDS)).isEqualTo(48, within(0.1));

        assertThat(percentiles[4].percentile()).isEqualTo(1);
        assertThat(percentiles[4].value(TimeUnit.SECONDS)).isEqualTo(48);
    }

    @Test
    @DisplayName("supports sending histograms of active task duration")
    default void histogram(MeterRegistry registry) {
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
    @DisplayName("attributes from @Timed annotation apply to builder")
    default void timedAnnotation(MeterRegistry registry) {
        Timed timed = AnnotationHolder.class.getAnnotation(Timed.class);
        LongTaskTimer ltt = LongTaskTimer.builder(timed).register(registry);
        Meter.Id id = ltt.getId();
        assertThat(id.getName()).isEqualTo("my.name");
        assertThat(id.getTags()).containsExactly(Tag.of("a", "tag"));
        assertThat(id.getDescription()).isEqualTo("some description");
        if (ltt instanceof CumulativeHistogramLongTaskTimer) {
            assertThat(ltt.takeSnapshot().histogramCounts()).isNotEmpty();
        }
    }

    @Timed(value = "my.name", longTask = true, extraTags = {"a", "tag"},
            description = "some description", histogram = true)
    class AnnotationHolder { }
}
