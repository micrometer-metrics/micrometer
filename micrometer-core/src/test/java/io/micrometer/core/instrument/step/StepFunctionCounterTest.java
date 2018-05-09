/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class StepFunctionCounterTest {
    @Test
    void count() {
        AtomicInteger n = new AtomicInteger(1);
        MockClock clock = new MockClock();
        StepFunctionCounter<AtomicInteger> counter = new StepFunctionCounter<>(
                new Meter.Id("my.counter", emptyList(), null, null, Meter.Type.COUNTER),
                clock, 1, n, AtomicInteger::get);

        assertThat(counter.count()).isEqualTo(0);
        clock.add(1, TimeUnit.MILLISECONDS);
        assertThat(counter.count()).isEqualTo(1);
    }
}
