/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.appdynamics;

import io.micrometer.appdynamics.reporter.AppDynamicsReporter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

public class AppDynamicsRegistryTest {

    private MockClock clock;

    private AppDynamicsConfig config;

    private AppDynamicsReporter reporter;

    private AppDynamicsRegistry victim;

    @BeforeEach
    void init() {
        clock = new MockClock();
        config = AppDynamicsConfig.DEFAULT;
        reporter = mock(AppDynamicsReporter.class);
        victim = new AppDynamicsRegistry(config, reporter, clock);
    }

    @Test
    void shouldPublishSumValue() {
        victim.counter("counter").increment(10d);
        clock.add(config.step());

        victim.publish();

        Meter.Id id = new Meter.Id("counter", Tags.empty(), null, null, Meter.Type.COUNTER);
        verify(reporter, times(1)).publishSumValue(id, 10);
    }

    @Test
    void shouldPublishObservationValue() {
        victim.gauge("gauge", 10);
        clock.add(config.step());

        victim.publish();

        Meter.Id id = new Meter.Id("gauge", Tags.empty(), null, null, Meter.Type.GAUGE);
        verify(reporter, times(1)).publishObservation(id, 10);
    }

    @Test
    void shouldPublishAggregationValue() {
        victim.timer("timer").record(100, TimeUnit.MILLISECONDS);
        clock.add(config.step());

        victim.publish();

        Meter.Id id = new Meter.Id("timer", Tags.empty(), null, null, Meter.Type.TIMER);
        verify(reporter, times(1)).publishAggregation(id, 1, 100, 100, 100);
    }

}
