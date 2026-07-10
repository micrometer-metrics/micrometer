/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.health.objectives.JvmServiceLevelObjectives;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthMeterRegistry}.
 *
 * @author Jon Schneider
 */
class HealthMeterRegistryTest {

    @Test
    void healthFromServiceLevelObjective() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .clock(new MockClock())
            .serviceLevelObjectives(ServiceLevelObjective.build("api.error.ratio")
                .failedMessage("API error ratio")
                .errorRatio(search -> search.name("http.server.requests").tag("uri", "$ENDPOINT"),
                        all -> all.tag("outcome", "SERVER_ERROR"))
                .isLessThan(0.01))
            .build();

        for (int i = 0; i < 100; i++) {
            Timer.builder("http.server.requests")
                .tag("outcome", i == 0 ? "SERVER_ERROR" : "SUCCESS")
                .tag("uri", "$ENDPOINT")
                .register(registry)
                .record(10, TimeUnit.MILLISECONDS);
        }

        clock(registry).add(Duration.ofSeconds(10));
        registry.tick();

        assertThat(registry.getServiceLevelObjectives().iterator().next().healthy(registry)).isFalse();

        for (int i = 0; i < 100; i++) {
            Timer.builder("http.server.requests")
                .tag("outcome", "SUCCESS")
                .tag("uri", "$ENDPOINT")
                .register(registry)
                .record(10, TimeUnit.MILLISECONDS);
        }

        clock(registry).add(Duration.ofSeconds(10));
        registry.tick();

        assertThat(registry.getServiceLevelObjectives().iterator().next().healthy(registry)).isTrue();
    }

    @Test
    void unmatchedServiceLevelObjectiveReportsHealthy() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .clock(new MockClock())
            .serviceLevelObjectives(ServiceLevelObjective.build("counter.throughput")
                .count(search -> search.name("my.counter"))
                .isGreaterThan(1))
            .build();

        assertThat(registry.getServiceLevelObjectives().iterator().next().healthy(registry)).isTrue();
    }

    @Test
    void onlyMetricsThatAreServiceLevelIndicatorsAreRegistered() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .clock(new MockClock())
            .serviceLevelObjectives(ServiceLevelObjective.build("counter.throughput")
                .count(search -> search.name("my.counter"))
                .isGreaterThan(1))
            .build();

        assertThat(registry.getMeters()).isEmpty();

        registry.counter("my.counter", "k", "v").increment();
        assertThat(registry.getMeters().size()).isEqualTo(1);

        registry.counter("another.counter").increment();
        assertThat(registry.getMeters().size()).isEqualTo(1);
    }

    @Test
    void applyRequiredBinders() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .clock(new MockClock())
            .serviceLevelObjectives(ServiceLevelObjective.build("counter.throughput")
                .requires(new JvmMemoryMetrics())
                .value(search -> search.name("jvm.memory.used"))
                .isGreaterThan(1))
            .build();

        assertThat(registry.getMeters().stream().map(m -> m.getId().getName())).containsOnly("jvm.memory.used");
    }

    @Test
    void meterFiltersAffectServiceLevelObjectives() {
        HealthMeterRegistry registry = HealthMeterRegistry.builder(HealthConfig.DEFAULT)
            .clock(new MockClock())
            .serviceLevelObjectives(JvmServiceLevelObjectives.MEMORY)
            .serviceLevelObjectiveFilter(MeterFilter.denyNameStartsWith("jvm.pool"))
            .serviceLevelObjectiveFilter(new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                    return id.getName().equals("jvm.gc.load") ? id.withName("jvm.collection.load") : id;
                }
            })
            .build();

        assertThat(registry.getServiceLevelObjectives().stream().map(ServiceLevelObjective::getName))
            .contains("jvm.collection.load")
            .doesNotContain("jvm.pool.memory");
    }

}
