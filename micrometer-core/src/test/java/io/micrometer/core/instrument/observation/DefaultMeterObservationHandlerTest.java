/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.instrument.observation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static io.micrometer.core.instrument.observation.DefaultMeterObservationHandler.IgnoredMeters.LONG_TASK_TIMER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMeterObservationHandler}. Additional tests can be found in
 * {@code MeterRegistryCompatibilityKit}.
 *
 * @author Jonatan Ivanov
 */
class DefaultMeterObservationHandlerTest {

    private ObservationRegistry observationRegistry;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.observationRegistry = ObservationRegistry.create();
        this.observationRegistry.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(this.meterRegistry));
    }

    @Test
    void shouldCreateAllMetersDuringAnObservationWithoutError() {
        Observation observation = Observation.createNotStarted("test.observation", observationRegistry)
            .lowCardinalityKeyValue("low", "1")
            .highCardinalityKeyValue("high", "2")
            .start()
            .event(Event.of("test.event"));

        assertThat(meterRegistry.get("test.observation.active").tags("low", "1").longTaskTimer().activeTasks())
            .isEqualTo(1);

        observation.stop();

        assertThat(meterRegistry.get("test.observation").tags("low", "1", "error", "none").timer().count())
            .isEqualTo(1);

        assertThat(meterRegistry.get("test.observation.active").tags("low", "1").longTaskTimer().activeTasks())
            .isEqualTo(0);

        assertThat(meterRegistry.get("test.observation.test.event").tags("low", "1").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldCreateAllMetersDuringAnObservationWithError() {
        Observation observation = Observation.createNotStarted("test.observation", observationRegistry)
            .lowCardinalityKeyValue("low", "1")
            .highCardinalityKeyValue("high", "2")
            .start()
            .event(Event.of("test.event"));

        assertThat(meterRegistry.get("test.observation.active").tags("low", "1").longTaskTimer().activeTasks())
            .isEqualTo(1);

        observation.error(new SocketTimeoutException("simulated")).stop();

        assertThat(meterRegistry.get("test.observation")
            .tags("low", "1", "error", "SocketTimeoutException")
            .timer()
            .count()).isEqualTo(1);

        assertThat(meterRegistry.get("test.observation.active").tags("low", "1").longTaskTimer().activeTasks())
            .isEqualTo(0);

        assertThat(meterRegistry.get("test.observation.test.event").tags("low", "1").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldNotCreateLongTaskTimerIfIgnored() {
        this.observationRegistry = ObservationRegistry.create();
        this.observationRegistry.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(this.meterRegistry, LONG_TASK_TIMER));

        Observation.createNotStarted("test.observation", observationRegistry)
            .lowCardinalityKeyValue("low", "1")
            .highCardinalityKeyValue("high", "2")
            .start()
            .event(Event.of("test.event"))
            .stop();

        assertThat(meterRegistry.get("test.observation").tags("low", "1", "error", "none").timer().count())
            .isEqualTo(1);

        assertThat(meterRegistry.get("test.observation.test.event").tags("low", "1").counter().count()).isEqualTo(1);

        assertThat(meterRegistry.find("test.observation.active").longTaskTimers()).isEmpty();
    }

}
