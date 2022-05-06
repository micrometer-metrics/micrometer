/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoopDistributionSummary}.
 *
 * @author Oleksii Bondar
 */
class NoopDistributionSummaryTest {

    private Id id = new Id("test", Tags.of("name", "value"), "ms", "", Type.DISTRIBUTION_SUMMARY);

    private NoopDistributionSummary distributionSummary = new NoopDistributionSummary(id);

    @Test
    void returnsId() {
        assertThat(distributionSummary.getId()).isEqualTo(id);
    }

    @Test
    void returnsCountAsZero() {
        assertThat(distributionSummary.count()).isEqualTo(0L);
    }

    @Test
    void returnsTotalAmountAsZero() {
        assertThat(distributionSummary.totalAmount()).isEqualTo(0L);
    }

    @Test
    void returnsMaxAsZero() {
        assertThat(distributionSummary.max()).isEqualTo(0L);
    }

    @Test
    void returnsEmptySnapshot() {
        HistogramSnapshot snapshot = distributionSummary.takeSnapshot();
        HistogramSnapshot expectedHistogram = HistogramSnapshot.empty(0, 0, 0);
        assertThat(snapshot.count()).isEqualTo(expectedHistogram.count());
        assertThat(snapshot.total()).isEqualTo(expectedHistogram.total());
        assertThat(snapshot.max()).isEqualTo(expectedHistogram.max());
    }

}
