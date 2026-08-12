/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionCounterTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void convertsCountUsingTimeUnit() {
        long countInMs = 1000;
        AtomicLong n = new AtomicLong(countInMs);
        FunctionCounter c = FunctionCounter
            .builder("my.time.counter", n, AtomicLong::doubleValue, TimeUnit.MILLISECONDS)
            .register(registry);

        double countInSeconds = TimeUtils.convert((double) countInMs, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
        assertThat(c.count()).describedAs("SimpleMeterRegistry has baseTimeUnit of seconds").isEqualTo(countInSeconds);
    }

    @Test
    void registryBaseTimeUnitIsUsed() {
        FunctionCounter functionCounter = FunctionCounter
            .builder("my.time.counter", new AtomicInteger(), AtomicInteger::doubleValue, TimeUnit.MILLISECONDS)
            .register(registry);

        assertThat(functionCounter.getId().getBaseUnit()).describedAs("SimpleMeterRegistry has baseTimeUnit of seconds")
            .isEqualTo("seconds");
    }

    @Test
    void baseUnitNotIgnoredWhenNotTimeBased() {
        FunctionCounter functionCounter = FunctionCounter
            .builder("jdbc.connections.created", new AtomicInteger(), AtomicInteger::doubleValue)
            .baseUnit("connections")
            .register(registry);

        assertThat(functionCounter.getId().getBaseUnit()).isEqualTo("connections");
    }

}
