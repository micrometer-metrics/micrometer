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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateDistributionSummaryTest {

    @Test
    void aggregateSnapshot() {
        MeterRegistry registry = new SimpleMeterRegistry();
        DistributionSummary summary1 = DistributionSummary.builder("my.summary.1")
                .publishPercentiles(0.5)
                .sla(10, 25)
                .publishPercentileHistogram()
                .register(registry);

        DistributionSummary summary2 = DistributionSummary.builder("my.summary.2")
                .publishPercentiles(0.5, 0.95)
                .sla(10, 25, 30)
                .publishPercentileHistogram()
                .register(registry);

        summary1.record(10);
        summary2.record(20);
        summary2.record(20);

        HistogramSnapshot snap = new AggregateDistributionSummary(summary1.getId(), Arrays.asList(summary1, summary2)).takeSnapshot(true);

        assertThat(snap.count()).isEqualTo(3);
        assertThat(snap.total()).isEqualTo(50);
        assertThat(snap.percentileValues()).isEmpty();
        assertThat(snap.histogramCounts())
                .contains(CountAtBucket.of(10, 1), CountAtBucket.of(25, 3))
                .hasSize(277);
    }
}