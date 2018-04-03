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
package io.micrometer.spring.filter;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.PropertiesMeterFilter;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.autoconfigure.ServiceLevelAgreementBoundary;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ConstantConditions")
public class PropertiesMeterFilterTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private MetricsProperties properties = new MetricsProperties();

    private PropertiesMeterFilter filter = new PropertiesMeterFilter(properties);

    @Test
    public void createWhenPropertiesIsNullShouldThrowException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Properties must not be null");
        new PropertiesMeterFilter(null);
    }

    @Test
    public void acceptWhenHasNoEnabledPropertiesShouldReturnNeutral() {
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    public void acceptWhenHasEnableFalseShouldReturnDeny() {
        enable("spring.boot", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    public void acceptWhenHasEnableTrueShouldReturnNeutral() {
        enable("spring.boot", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    public void acceptWhenHasHigherEnableFalseShouldReturnDeny() {
        enable("spring", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    public void acceptWhenHasHigherEnableTrueShouldReturnNeutral() {
        enable("spring", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    public void acceptWhenHasHigherEnableFalseExactEnableTrueShouldReturnNeutral() {
        enable("spring", false);
        enable("spring.boot", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    public void acceptWhenHasHigherEnableTrueExactEnableFalseShouldReturnDeny() {
        enable("spring", true);
        enable("spring.boot", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    public void acceptWhenHasAllEnableFalseShouldReturnDeny() {
        enable("all", false);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    public void acceptWhenHasAllEnableFalseButHigherEnableTrueShouldReturnNeutral() {
        enable("all", false);
        enable("spring", true);
        assertThat(filter.accept(createSpringBootMeter()))
                .isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    public void configureWhenHasHistogramTrueShouldSetPercentilesHistogramToTrue() {
        percentilesHistogram("spring.boot", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    public void configureWhenHasHistogramFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring.boot", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    public void configureWhenHasHigherHistogramTrueShouldSetPercentilesHistogramToTrue() {
        percentilesHistogram("spring", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    public void configureWhenHasHigherHistogramFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    public void configureWhenHasHigherHistogramTrueAndLowerFalseShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", true);
        percentilesHistogram("spring.boot", false);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isFalse();
    }

    @Test
    public void configureWhenHasHigherHistogramFalseAndLowerTrueShouldSetPercentilesHistogramToFalse() {
        percentilesHistogram("spring", false);
        percentilesHistogram("spring.boot", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    public void configureWhenAllHistogramTrueSetPercentilesHistogramToTrue() {
        percentilesHistogram("all", true);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .isPercentileHistogram()).isTrue();
    }

    @Test
    public void configureWhenHasPercentilesShouldSetPercentilesToValue() {
        percentiles("spring.boot", 0.5, 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5, 0.9);
    }

    @Test
    public void configureWhenHasHigherPercentilesShouldSetPercentilesToValue() {
        percentiles("spring", 0.5, 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5, 0.9);
    }

    @Test
    public void configureWhenHasHigherPercentilesAndLowerShouldSetPercentilesToHigher() {
        percentiles("spring", 0.5);
        percentiles("spring.boot", 0.9);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.9);
    }

    @Test
    public void configureWhenAllPercentilesSetShouldSetPercentilesToValue() {
        percentiles("all", 0.5);
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getPercentiles()).containsExactly(0.5);
    }

    @Test
    public void configureWhenHasSlaShouldSetSlaToValue() {
        slas("spring.boot", "1", "2", "3");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(1000000, 2000000, 3000000);
    }

    @Test
    public void configureWhenHasHigherSlaShouldSetPercentilesToValue() {
        slas("spring", "1", "2", "3");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(1000000, 2000000, 3000000);
    }

    @Test
    public void configureWhenHasHigherSlaAndLowerShouldSetSlaToHigher() {
        slas("spring", "1", "2", "3");
        slas("spring.boot", "4", "5", "6");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(4000000, 5000000, 6000000);
    }

    @Test
    public void configureWhenAllSlaSetShouldSetSlaToValue() {
        slas("all", "1", "2", "3");
        assertThat(filter.configure(createSpringBootMeter(), DistributionStatisticConfig.DEFAULT)
                .getSlaBoundaries()).containsExactly(1000000, 2000000, 3000000);
    }

    @Test
    public void configureWhenSlaDurationShouldOnlyApplyToTimer() {
        slas("all", "1ms", "2ms", "3ms");
        Meter.Id timer = createSpringBootMeter(Meter.Type.TIMER);
        Meter.Id summary = createSpringBootMeter(Meter.Type.DISTRIBUTION_SUMMARY);
        Meter.Id counter = createSpringBootMeter(Meter.Type.COUNTER);
        assertThat(filter.configure(timer, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .containsExactly(1000000, 2000000, 3000000);
        assertThat(filter.configure(summary, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .isNull();
        assertThat(filter.configure(counter, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .isNull();
    }

    @Test
    public void configureWhenSlaLongShouldOnlyApplyToTimerAndDistributionSummary() {
        slas("all", "1", "2", "3");
        Meter.Id timer = createSpringBootMeter(Meter.Type.TIMER);
        Meter.Id summary = createSpringBootMeter(Meter.Type.DISTRIBUTION_SUMMARY);
        Meter.Id counter = createSpringBootMeter(Meter.Type.COUNTER);
        assertThat(filter.configure(timer, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .containsExactly(1000000, 2000000, 3000000);
        assertThat(filter.configure(summary, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .containsExactly(1, 2, 3);
        assertThat(filter.configure(counter, DistributionStatisticConfig.DEFAULT).getSlaBoundaries())
                .isNull();
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
}