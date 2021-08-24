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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.utils.Pair;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.micrometer.cloudwatch2.CloudWatchUtils.histogramCountsToCloudWatchArrays;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test methods in CloudWatchUtils
 */
class CloudWatchUtilsTest {

    private static final double EXPECTED_MIN = 8.515920e-109;
    private static final double EXPECTED_MAX = 1.174271e+108;

    @Test
    void testClamp() {
        assertThat(CloudWatchUtils.clampMetricValue(Double.NaN))
                .as("Check NaN")
                .isNaN();

        assertThat(CloudWatchUtils.clampMetricValue(Double.MIN_VALUE))
                .as("Check minimum value")
                .isEqualTo(EXPECTED_MIN);

        assertThat(CloudWatchUtils.clampMetricValue(Double.NEGATIVE_INFINITY))
                .as("Check negative infinity")
                .isEqualTo(-EXPECTED_MAX);

        assertThat(CloudWatchUtils.clampMetricValue(Double.POSITIVE_INFINITY))
                .as("Check positive infinity")
                .isEqualTo(EXPECTED_MAX);

        assertThat(CloudWatchUtils.clampMetricValue(-Double.MAX_VALUE))
                .as("Check negative max value")
                .isEqualTo(-EXPECTED_MAX);

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

    @Test
    void serialiseHistogramCounts() {
        CountAtBucket[] histogramData = new CountAtBucket[]{
                new CountAtBucket(1.0, 1.0),
                new CountAtBucket(5.0, 1.0),
                new CountAtBucket(10.0, 5.0),
                new CountAtBucket(15.0, 5.0),
                new CountAtBucket(20.0, 6.0),
                new CountAtBucket(25.0, 25.0),
                new CountAtBucket(30.0, 25.0),
        };

        final List<Pair<List<Double>, List<Double>>> pairs = histogramCountsToCloudWatchArrays(histogramData, TimeUnit.MILLISECONDS);

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).right()).hasSize(4);
        assertThat(pairs.get(0).right().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(ints = {149, 150, 151, 300, 500, 652})
    void serialiseHistogramCountsBatched(int sampleCount) {
        CountAtBucket[] histogramData = new CountAtBucket[sampleCount];
        int c = 0;
        for (int i = 0; i < sampleCount; i++) {
            c += ThreadLocalRandom.current().nextInt(1, 5);
            histogramData[i] = new CountAtBucket((double) i, c);
        }

        final List<Pair<List<Double>, List<Double>>> pairs = histogramCountsToCloudWatchArrays(histogramData, TimeUnit.MILLISECONDS);

        assertThat(pairs).hasSize((sampleCount / 150) + (sampleCount % 150 == 0 ? 0 : 1));
        assertThat(pairs.stream()
                .map(it -> it.right()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .sum())
                .mapToDouble(Double::doubleValue).sum())
                .isEqualTo(c);
    }
}
