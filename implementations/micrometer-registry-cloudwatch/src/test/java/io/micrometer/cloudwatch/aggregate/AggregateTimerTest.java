/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateTimerTest {

    @Test
    void aggregateSnapshot() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer timer1 = Timer.builder("my.timer.1")
                .publishPercentiles(0.5)
                .sla(Duration.ofMillis(10), Duration.ofMillis(25))
                .publishPercentileHistogram()
                .register(registry);

        Timer timer2 = Timer.builder("my.timer.2")
                .publishPercentiles(0.5, 0.95)
                .sla(Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(30))
                .publishPercentileHistogram()
                .register(registry);

        timer1.record(10, TimeUnit.MILLISECONDS);
        timer2.record(20, TimeUnit.MILLISECONDS);
        timer2.record(20, TimeUnit.MILLISECONDS);

        HistogramSnapshot snap = new AggregateTimer(timer1.getId(), Arrays.asList(timer1, timer2)).takeSnapshot(true);

        assertThat(snap.count()).isEqualTo(3);
        assertThat(snap.total(TimeUnit.MILLISECONDS)).isEqualTo(50);
        assertThat(snap.percentileValues()).isEmpty();
        assertThat(snap.histogramCounts())
                .contains(CountAtBucket.of(Duration.ofMillis(10).toNanos(), 1),
                        CountAtBucket.of(Duration.ofMillis(25).toNanos(), 3))
                .hasSize(70);
    }
}