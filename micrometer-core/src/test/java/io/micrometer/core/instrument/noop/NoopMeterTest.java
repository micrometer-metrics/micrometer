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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoopMeter}.
 *
 * @author Oleksii Bondar
 */
class NoopMeterTest {

    private Id id = new Id("test", Tags.of("name", "value"), "", "", Type.OTHER);

    private NoopMeter meter = new NoopMeter(id);

    @Test
    void returnsId() {
        assertThat(meter.getId()).isEqualTo(id);
    }

    @Test
    void returnsMeasureAsEmptyList() {
        assertThat(meter.measure()).isEqualTo(Collections.emptyList());
    }

}
