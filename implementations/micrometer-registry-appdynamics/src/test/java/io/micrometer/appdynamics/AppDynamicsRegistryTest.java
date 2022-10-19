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

import com.appdynamics.agent.api.MetricPublisher;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

public class AppDynamicsRegistryTest {

    private MockClock clock;

    private MetricPublisher publisher;

    private AppDynamicsConfig config;

    private AppDynamicsRegistry registry;

    @BeforeEach
    void init() {
        clock = new MockClock();
        config = AppDynamicsConfig.DEFAULT;
        publisher = mock(MetricPublisher.class);
        registry = new AppDynamicsRegistry(config, publisher, clock);
    }

    @Test
    void shouldPublishSumValue() {
        registry.counter("counter").increment(10d);
        clock.add(config.step());

        registry.publish();

        verify(publisher).reportMetric(config.group() + "counter", 10, "SUM", "SUM", "COLLECTIVE");
    }

    @Test
    void shouldPublishAverageValue() {
        registry.timer("timer").record(100, TimeUnit.MILLISECONDS);
        clock.add(config.step());

        registry.publish();

        verify(publisher).reportMetric(config.group() + "timer", 100, 1, 100, 100, "AVERAGE", "AVERAGE", "INDIVIDUAL");
    }

    @Test
    void shouldPublishObservationValue() {
        registry.gauge("gauge", 10);
        clock.add(config.step());

        registry.publish();

        verify(publisher).reportMetric(config.group() + "gauge", 10, "OBSERVATION", "CURRENT", "COLLECTIVE");
    }

}
