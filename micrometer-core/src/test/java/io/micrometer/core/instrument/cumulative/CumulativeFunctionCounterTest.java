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
package io.micrometer.core.instrument.cumulative;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CumulativeFunctionCounter}.
 */
class CumulativeFunctionCounterTest {

    @Test
    void shouldKeepLastValueWhenFunctionThrows() {
        Meter.Id id = new Meter.Id("name", Tags.empty(), null, null, Meter.Type.COUNTER);
        AtomicBoolean fail = new AtomicBoolean();
        CumulativeFunctionCounter<Object> counter = new CumulativeFunctionCounter<>(id, new Object(), (o) -> {
            if (fail.get()) {
                throw new IllegalStateException("simulated");
            }
            return 3d;
        });
        assertThat(counter.count()).isEqualTo(3d);
        fail.set(true);
        assertThat(counter.count()).isEqualTo(3d);
    }

}
