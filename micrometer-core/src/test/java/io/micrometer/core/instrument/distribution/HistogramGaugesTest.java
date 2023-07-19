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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HistogramGaugesTest {

    @Test
    void snapshotRollsOverAfterEveryPublish() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);

        HistogramGauges gauges = HistogramGauges.registerWithCommonFormat(timer, registry);

        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(registry.get("my.timer.histogram").gauge().value()).isEqualTo(1);
        assertThat(gauges.polledGaugesLatch.getCount()).isEqualTo(0);
    }

    @Test
    void meterFiltersAreOnlyAppliedOnceToHistogramsAndPercentiles() {
        MeterRegistry registry = new SimpleMeterRegistry();

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("MYPREFIX." + id.getName());
            }
        });

        Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofMillis(1))
            .publishPercentiles(0.95)
            .register(registry);

        registry.get("MYPREFIX.my.timer.percentile").tag("phi", "0.95").gauge();
        registry.get("MYPREFIX.my.timer.histogram").tag("le", "0.001").gauge();
    }

    @Test
    void histogramsContainLongMaxValue() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Timer timer = Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofNanos(Long.MAX_VALUE))
            .register(registry);

        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution")
            .serviceLevelObjectives(Double.POSITIVE_INFINITY)
            .register(registry);

        HistogramGauges distributionGauges = HistogramGauges.registerWithCommonFormat(distributionSummary, registry);

        HistogramGauges timerGauges = HistogramGauges.registerWithCommonFormat(timer, registry);

        assertThat(registry.get("my.distribution.histogram").tag("le", "+Inf").gauge()).isNotNull();
        assertThat(registry.get("my.timer.histogram").tag("le", "+Inf").gauge()).isNotNull();
    }

}
