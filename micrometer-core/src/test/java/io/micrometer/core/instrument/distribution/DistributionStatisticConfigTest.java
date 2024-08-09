/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Jon Schneider
 */
class DistributionStatisticConfigTest {

    @Test
    void merge() {
        DistributionStatisticConfig c1 = DistributionStatisticConfig.builder().percentiles(0.95).build();
        DistributionStatisticConfig c2 = DistributionStatisticConfig.builder().percentiles(0.90).build();

        DistributionStatisticConfig merged = c2.merge(c1).merge(DistributionStatisticConfig.DEFAULT);

        assertThat(merged.getPercentiles()).containsExactly(0.90);
        assertThat(merged.getExpiry()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void bufferLengthValidation() {
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().bufferLength(0).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().bufferLength(-1).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
    }

    @Test
    void serviceLevelObjectiveBoundariesValidation() {
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().serviceLevelObjectives(0.0).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().serviceLevelObjectives(-1).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().serviceLevelObjectives(-1, 2, 10).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
    }

    @Test
    void percentilesValidation() {
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().percentiles(-0.01).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().percentiles(1.01).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
    }

    @Test
    void expectedValueRangeValidation() {
        assertThatThrownBy(() -> DistributionStatisticConfig.builder().minimumExpectedValue(0.0).build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
        assertThatThrownBy(() -> DistributionStatisticConfig.builder()
            .minimumExpectedValue(10.0)
            .maximumExpectedValue(9.0)
            .build())
            .satisfies(cause -> assertThat(cause.getMessage()).startsWith("Invalid distribution configuration:"));
    }

}
