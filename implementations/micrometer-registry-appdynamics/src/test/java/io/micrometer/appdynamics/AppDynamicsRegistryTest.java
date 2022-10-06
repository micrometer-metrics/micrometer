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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
                reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.counter("counter").increment(10d);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|counter, 10, SUM, SUM, COLLECTIVE]", Arrays.toString(reference.get()));
    }

    @Test
    void shouldPublishAverageValue() {
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
            reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.timer("timer").record(100, TimeUnit.MILLISECONDS);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|timer, 100, 1, 100, 100, AVERAGE, AVERAGE, INDIVIDUAL]",
                Arrays.toString(reference.get()));
    }

    @Test
    void shouldPublishObservationValue() {
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
            reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.gauge("gauge", 10);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|gauge, 10, OBSERVATION, CURRENT, COLLECTIVE]",
                Arrays.toString(reference.get()));
    }

}
