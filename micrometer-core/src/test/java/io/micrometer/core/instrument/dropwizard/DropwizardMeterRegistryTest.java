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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DropwizardMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class DropwizardMeterRegistryTest {

    private final MockClock clock = new MockClock();

    private final DropwizardConfig config = new DropwizardConfig() {
        @Override
        public String prefix() {
            return "dropwizard";
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    };

    private final DropwizardMeterRegistry registry = new DropwizardMeterRegistry(config, new MetricRegistry(),
            HierarchicalNameMapper.DEFAULT, clock) {
        @Override
        protected Double nullGaugeValue() {
            return Double.NaN;
        }
    };

    @Test
    void gaugeOnNullValue() {
        registry.gauge("gauge", emptyList(), null, obj -> 1.0);
        assertThat(registry.get("gauge").gauge().value()).isNaN();
    }

    @Test
    void customMeasurementsThatDifferOnlyInTagValue() {
        Meter
            .builder("my.custom", Meter.Type.GAUGE,
                    Arrays.asList(new Measurement(() -> 1.0, Statistic.COUNT),
                            new Measurement(() -> 2.0, Statistic.TOTAL)))
            .register(registry);
    }

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0, 2)
            .register(registry);

        summary.record(1);

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "1").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(timerHist.value()).isEqualTo(1);

        clock.add(config.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(timerHist.value()).isEqualTo(0);
    }

    @Issue("#1038")
    @Test
    void removeShouldWork() {
        Counter counter = registry.counter("test");
        assertThat(registry.getDropwizardRegistry().getMeters()).hasSize(1);
        registry.remove(counter);
        assertThat(registry.getDropwizardRegistry().getMeters()).isEmpty();
    }

    @Issue("#2924")
    @Test
    void removeShouldWorkForLongTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("foo").register(registry);
        assertThat(registry.getDropwizardRegistry().getGauges()).hasSize(3);
        registry.remove(timer);
        assertThat(registry.getDropwizardRegistry().getGauges()).isEmpty();
    }

}
