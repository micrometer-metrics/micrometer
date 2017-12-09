package io.micrometer.cloudwatch;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test methods in CloudWatchUtils
 */
class CloudWatchUtilsTest {

    private static final double EXPECTED_MIN = 8.515920e-109;
    private static final double EXPECTED_MAX = 1.174271e+108;

    @Test
    void testClamp() {
        Assertions.assertThat(CloudWatchUtils.clampMetricValue(Double.NaN))
            .as("Check NaN")
            .isEqualTo(Double.NaN);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(Double.MIN_VALUE))
            .as("Check minimum value")
            .isEqualTo(EXPECTED_MIN);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(Double.NEGATIVE_INFINITY))
            .as("Check negative infinity")
            .isEqualTo(-EXPECTED_MAX);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(Double.POSITIVE_INFINITY))
            .as("Check positive infinity")
            .isEqualTo(EXPECTED_MAX);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(-Double.MAX_VALUE))
            .as("Check negative max value")
            .isEqualTo(-EXPECTED_MAX);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(0))
            .as("Check 0")
            .isEqualTo(0);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(-0))
            .as("Check -0")
            .isEqualTo(0);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(100.1))
            .as("Check positive value")
            .isEqualTo(100.1);

        Assertions.assertThat(CloudWatchUtils.clampMetricValue(-10.2))
            .as("Check negative value")
            .isEqualTo(-10.2);
    }

}
