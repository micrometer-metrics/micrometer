/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link PropertiesMeterFilter}.
 *
 * @author Phillip Webb
 * @author Jon Schneider
 * @author Artsiom Yudovin
 */
@SuppressWarnings("ConstantConditions")
class PropertiesMeterFilterTest {

    private MetricsProperties properties = new MetricsProperties();

    private PropertiesMeterFilter filter = new PropertiesMeterFilter(properties);

    @Test
    void createWhenPropertiesIsNullShouldThrowException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new PropertiesMeterFilter(null);
        });
        assertThat(ex.getMessage()).isEqualTo("Properties must not be null");
    }

    @Test
    void acceptWhenHasNoEnabledPropertiesShouldReturnNeutral() {
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void acceptWhenHasNoMatchingEnabledPropertyShouldReturnNeutral() {
        properties.getEnable().put("something.else", false);
        assertThat(filter.accept(createSpringBootMeter()))
            .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void acceptWhenHasEnableFalseShouldReturnDeny() {
        enable("spring.boot", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    void acceptWhenHasEnableTrueShouldReturnNeutral() {
        enable("spring.boot", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void acceptWhenHasHigherEnableFalseShouldReturnDeny() {
        enable("spring", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    void acceptWhenHasHigherEnableTrueShouldReturnNeutral() {
        enable("spring", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void acceptWhenHasHigherEnableFalseExactEnableTrueShouldReturnNeutral() {
        enable("spring", false);
        enable("spring.boot", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void acceptWhenHasHigherEnableTrueExactEnableFalseShouldReturnDeny() {
        enable("spring", true);
        enable("spring.boot", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    void acceptWhenHasAllEnableFalseShouldReturnDeny() {
        enable("all", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    void acceptWhenHasAllEnableFalseButHigherEnableTrueShouldReturnNeutral() {
        enable("all", false);
        enable("spring", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    void configureWhenHasHistogramTrueShouldSetPercentilesHistogramToTrue() {
        percentilesHistogram("spring.boot", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    void configureWhenHasHistogramFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring.boot", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    void configureWhenHasHigherHistogramTrueShouldSetPercentilesHistogramToTrue() {
        percentilesHistogram("spring", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    void configureWhenHasHigherHistogramFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    void configureWhenHasHigherHistogramTrueAndLowerFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", true);
        percentilesHistogram("spring.boot", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    void configureWhenHasHigherHistogramFalseAndLowerTrueShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", false);
        percentilesHistogram("spring.boot", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    void configureWhenAllHistogramTrueSetPercentilesHistogramToTrue() {
        percentilesHistogram("all", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    void configureWhenHasPercentilesShouldSetPercentilesToValue() {
        percentiles("spring.boot", 0.5, 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5, 0.9);
    }

    @Test
    void configureWhenHasHigherPercentilesShouldSetPercentilesToValue() {
        percentiles("spring", 0.5, 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5, 0.9);
    }

    @Test
    void configureWhenHasHigherPercentilesAndLowerShouldSetPercentilesToHigher() {
        percentiles("spring", 0.5);
        percentiles("spring.boot", 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.9);
    }

    @Test
    void configureWhenAllPercentilesSetShouldSetPercentilesToValue() {
        percentiles("all", 0.5);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5);
    }

    @Test
    void configureWhenHasSlaShouldSetSlaToValue() {
        slas("spring.boot", "1", "2", "3");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(1000000, 2000000, 3000000);
    }

    @Test
    void configureWhenHasHigherSlaShouldSetPercentilesToValue() {
        slas("spring", "1", "2", "3");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(1000000, 2000000, 3000000);
    }

    @Test
    void configureWhenHasHigherSlaAndLowerShouldSetSlaToHigher() {
        slas("spring", "1", "2", "3");
        slas("spring.boot", "4", "5", "6");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(4000000, 5000000, 6000000);
    }

    @Test
    void configureWhenHasMinimumExpectedValueShouldSetMinimumExpectedToValue() {
        setMinimumExpectedValue("spring.boot", 10);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMinimumExpectedValue())
                        .isEqualTo(Duration.ofMillis(10).toNanos());
    }

    @Test
    void configureWhenHasHigherMinimumExpectedValueShouldSetMinimumExpectedValueToValue() {
        setMinimumExpectedValue("spring", 10);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMinimumExpectedValue())
                        .isEqualTo(Duration.ofMillis(10).toNanos());
    }

    @Test
    void configureWhenHasHigherMinimumExpectedValueAndLowerShouldSetMinimumExpectedValueToHigher() {
        setMinimumExpectedValue("spring", 10);
        setMinimumExpectedValue("spring.boot", 50);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMinimumExpectedValue())
                        .isEqualTo(Duration.ofMillis(50).toNanos());
    }

    @Test
    void configureWhenHasMaximumExpectedValueShouldSetMaximumExpectedToValue() {
        setMaximumExpectedValue("spring.boot", 5000);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMaximumExpectedValue())
                        .isEqualTo(Duration.ofMillis(5000).toNanos());
    }

    @Test
    void configureWhenHasHigherMaximumExpectedValueShouldSetMaximumExpectedValueToValue() {
        setMaximumExpectedValue("spring", 5000);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMaximumExpectedValue())
                        .isEqualTo(Duration.ofMillis(5000).toNanos());
    }

    @Test
    void configureWhenHasHigherMaximumExpectedValueAndLowerShouldSetMaximumExpectedValueToHigher() {
        setMaximumExpectedValue("spring", 5000);
        setMaximumExpectedValue("spring.boot", 10000);
        assertThat(filter.configure(createSpringBootMeter(),
                DistributionStatisticConfig.DEFAULT).getMaximumExpectedValue())
                        .isEqualTo(Duration.ofMillis(10000).toNanos());
    }

    private Meter.Id createSpringBootMeter() {
        Meter.Type meterType = Meter.Type.TIMER;
        return createSpringBootMeter(meterType);
    }

    private Meter.Id createSpringBootMeter(Meter.Type meterType) {
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, Clock.SYSTEM);
        return Meter.builder("spring.boot", meterType, Collections.emptyList()).register(registry)
                .getId();
    }

    private void enable(String metricPrefix, boolean enabled) {
        properties.getEnable().put(metricPrefix, enabled);
    }

    private void percentilesHistogram(String metricPrefix, boolean enabled) {
        properties.getDistribution().getPercentilesHistogram().put(metricPrefix, enabled);
    }

    private void percentiles(String metricPrefix, double... percentiles) {
        properties.getDistribution().getPercentiles().put(metricPrefix, percentiles);
    }

    private void slas(String metricPrefix, String... slas) {
        properties.getDistribution().getSla().put(metricPrefix,
                Arrays.stream(slas).map(ServiceLevelAgreementBoundary::valueOf)
                        .toArray(ServiceLevelAgreementBoundary[]::new));
    }

    private void setMinimumExpectedValue(String metricPrefix, long minimumExpectedValue) {
        properties.getDistribution().getMinimumExpectedValue()
                .put(metricPrefix, Long.toString(minimumExpectedValue));
    }

    private void setMaximumExpectedValue(String metricPrefix, long maximumExpectedValue) {
        properties.getDistribution().getMaximumExpectedValue()
                .put(metricPrefix, Long.toString(maximumExpectedValue));
    }

}
