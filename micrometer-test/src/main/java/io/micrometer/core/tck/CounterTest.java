/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertEquals;

interface CounterTest {
    Duration step();

    @DisplayName("multiple increments are maintained")
    @Test
    default void increment(MeterRegistry registry) {
        Counter c = registry.counter("myCounter");
        c.increment();
        clock(registry).add(step());
        assertThat(c.count()).isEqualTo(1.0, offset(1e-12));
        c.increment();
        c.increment();
        clock(registry).add(step());

        // in the case of a step aggregating system will be 2, otherwise 3
        assertThat(c.count()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("increment by a non-negative amount")
    default void incrementAmount(MeterRegistry registry) {
        Counter c = registry.counter("myCounter");
        c.increment(2);
        c.increment(0);
        clock(registry).add(step());

        assertEquals(2L, c.count());
    }

    @Test
    @DisplayName("function-tracking counter increments by change in a monotonically increasing function when observed")
    default void functionTrackingCounter(MeterRegistry registry) {
        AtomicLong n = new AtomicLong(0);
        registry.more().counter("tracking", emptyList(), n);
        n.incrementAndGet();

        clock(registry).add(step());
        registry.forEachMeter(Meter::measure);
        assertThat(registry.get("tracking").functionCounter().count()).isEqualTo(1.0);
    }
}
