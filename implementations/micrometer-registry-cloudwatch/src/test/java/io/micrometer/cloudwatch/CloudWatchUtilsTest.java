/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.cloudwatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test methods in CloudWatchUtils
 */
class CloudWatchUtilsTest {

    @Test
    void testClamp() {
        assertThat(CloudWatchUtils.clampMetricValue(Double.NaN))
                .as("Check NaN")
                .isEqualTo(Double.NaN);

        assertThat(CloudWatchUtils.clampMetricValue(Double.MIN_VALUE))
                .as("Check minimum value")
                .isEqualTo(CloudWatchUtils.MINIMUM_ALLOWED_VALUE);

        assertThat(CloudWatchUtils.clampMetricValue(Double.NEGATIVE_INFINITY))
                .as("Check negative infinity")
                .isEqualTo(-CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);

        assertThat(CloudWatchUtils.clampMetricValue(Double.POSITIVE_INFINITY))
                .as("Check positive infinity")
                .isEqualTo(CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);

        assertThat(CloudWatchUtils.clampMetricValue(-Double.MAX_VALUE))
                .as("Check negative max value")
                .isEqualTo(-CloudWatchUtils.MAXIMUM_ALLOWED_VALUE);

        assertThat(CloudWatchUtils.clampMetricValue(0))
                .as("Check 0")
                .isEqualTo(0);

        assertThat(CloudWatchUtils.clampMetricValue(-0))
                .as("Check -0")
                .isEqualTo(0);

        assertThat(CloudWatchUtils.clampMetricValue(100.1))
                .as("Check positive value")
                .isEqualTo(100.1);

        assertThat(CloudWatchUtils.clampMetricValue(-10.2))
                .as("Check negative value")
                .isEqualTo(-10.2);
    }
}
