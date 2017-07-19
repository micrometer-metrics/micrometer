/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @DisplayName("multiple increments are maintained")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void increment(MeterRegistry registry) {
        Counter c = registry.counter("myCounter");
        c.increment();
        assertEquals(1L, c.count());
        c.increment();
        c.increment();
        assertEquals(3L, c.count());
    }

    @DisplayName("increment by a non-negative amount")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void incrementAmount(MeterRegistry registry) {
        Counter c = registry.counter("myCounter");
        c.increment(2);
        assertEquals(2L, c.count());
        c.increment(0);
        assertEquals(2L, c.count());
    }

    @DisplayName("increment by a negative amount")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void incrementAmountNegative(MeterRegistry registry) {
        if(registry instanceof PrometheusMeterRegistry) {
            // Prometheus does not support decrementing counters
            return;
        }

        Counter c = registry.counter("myCounter");
        c.increment(-2);
        assertEquals(-2L, c.count());
    }
}
