/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.api.instrument.noop;

import io.micrometer.api.instrument.Meter.Id;
import io.micrometer.api.instrument.Meter.Type;
import io.micrometer.api.instrument.Tags;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoopFunctionCounter}.
 *
 * @author Oleksii Bondar
 */
class NoopFunctionCounterTest {

    private Id id = new Id("test", Tags.of("name", "value"), "entries", "", Type.COUNTER);
    private NoopFunctionCounter counter = new NoopFunctionCounter(id);

    @Test
    void returnsId() {
        assertThat(counter.getId()).isEqualTo(id);
    }

    @Test
    void returnsCountAsZero() {
        assertThat(counter.count()).isEqualTo(0L);
    }
}
