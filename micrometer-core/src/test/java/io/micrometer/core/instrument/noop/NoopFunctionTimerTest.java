/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoopFunctionTimer}.
 *
 * @author Oleksii Bondar
 */
class NoopFunctionTimerTest {

    private Id id = new Id("test", Tags.of("name", "value"), "ms", "", Type.TIMER);
    private NoopFunctionTimer timer = new NoopFunctionTimer(id);

    @Test
    void returnsId() {
        assertThat(timer.getId()).isEqualTo(id);
    }

    @Test
    void returnsCountAsZero() {
        assertThat(timer.count()).isEqualTo(0d);
    }

    @Test
    void returnsTotalTimeAsZero() {
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(0d);
    }

    @Test
    void returnsNanosAsTimeUnit() {
        assertThat(timer.baseTimeUnit()).isEqualTo(TimeUnit.NANOSECONDS);
    }
}
