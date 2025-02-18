/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.stackdriver;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@Tag("gcp-it")
class StackdriverIntegrationTest {

    static final StackdriverConfig CONFIG = new StackdriverConfig() {
        @Override
        public String projectId() {
            return "micrometer-gcp";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    StackdriverMeterRegistry registry = new StackdriverMeterRegistry(CONFIG, Clock.SYSTEM);

    StackdriverMeterRegistry.Batch batch = registry.new Batch();

    @AfterEach
    void cleanUp() {
        registry.close();
        // TODO assert no warnings logged
    }

    @Test
    void timerWithSlos() {
        Timer slos = Timer.builder("micrometer.stackdriver.it.timer.slo")
            .maximumExpectedValue(Duration.ofMillis(2))
            .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(2))
            .register(registry);
        slos.record(Duration.ofMillis(1));
        slos.record(Duration.ofMillis(2));
        slos.record(Duration.ofMillis(3));

        System.out.println(slos.getId().toString() + batch.distribution(slos.takeSnapshot(), true));
    }

    @Test
    void timerWithPercentileHistogram() {
        Timer percentileHistogram = Timer.builder("micrometer.stackdriver.it.timer.percentilehistogram")
            .publishPercentileHistogram()
            .register(registry);

        percentileHistogram.record(Duration.ofMillis(1));
        percentileHistogram.record(Duration.ofMillis(2));
        percentileHistogram.record(Duration.ofMillis(3));

        System.out.println(
                percentileHistogram.getId().toString() + batch.distribution(percentileHistogram.takeSnapshot(), true));
    }

    @Test
    void timerWithClientPercentiles() {
        Timer clientPercentiles = Timer.builder("micrometer.stackdriver.it.timer.clientpercentiles")
            .publishPercentiles(0.5, 0.99)
            .register(registry);
        clientPercentiles.record(Duration.ofMillis(1));
        clientPercentiles.record(Duration.ofMillis(2));
        clientPercentiles.record(Duration.ofMillis(3));

        System.out
            .println(clientPercentiles.getId().toString() + batch.distribution(clientPercentiles.takeSnapshot(), true));
    }

    @Test
    void timerWithClientPercentilesAndSlo() {
        Timer clientPercentilesAndSlos = Timer.builder("micrometer.stackdriver.it.timer.clientpercentilesandslo")
            .serviceLevelObjectives(Duration.ofMillis(2))
            .publishPercentiles(0.5, 0.99)
            .register(registry);
        clientPercentilesAndSlos.record(Duration.ofMillis(1));
        clientPercentilesAndSlos.record(Duration.ofMillis(2));
        clientPercentilesAndSlos.record(Duration.ofMillis(3));

        System.out.println(clientPercentilesAndSlos.getId().toString()
                + batch.distribution(clientPercentilesAndSlos.takeSnapshot(), true));
    }

    @Test
    void summary() {
        DistributionSummary summary = DistributionSummary.builder("micrometer.stackdriver.it.summary")
            .register(registry);
        summary.record(10d);
        summary.record(20d);
        summary.record(30d);

        System.out.println(summary.getId().toString() + batch.distribution(summary.takeSnapshot(), false));
    }

    @Test
    void summaryWithOneSlo() {
        DistributionSummary oneSlo = DistributionSummary.builder("micrometer.stackdriver.it.summaryoneslo")
            .serviceLevelObjectives(20)
            .register(registry);
        oneSlo.record(10d);
        oneSlo.record(20d);
        oneSlo.record(30d);

        System.out.println(oneSlo.getId().toString() + batch.distribution(oneSlo.takeSnapshot(), false));
    }

}
