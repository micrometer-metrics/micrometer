/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyableTest {

    private final MockClock clock = new MockClock();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

    @Test
    void counterBuilderShouldBeCopyable() {
        final Counter.Builder builder = Counter.builder("counter")
            .tags("tag", "value")
            .description("description")
            .baseUnit("baseUnit");

        final Counter.Builder copy = builder.copy();

        builder.tags("key", "append").description("override").baseUnit("replace");

        final Counter modified = builder.register(registry);
        final Counter original = copy.register(registry);

        assertThat(original.getId().getName()).isEqualTo("counter");
        assertThat(original.getId().getTags()).containsExactly(Tag.of("tag", "value"));
        assertThat(original.getId().getDescription()).isEqualTo("description");
        assertThat(original.getId().getBaseUnit()).isEqualTo("baseUnit");

        assertThat(modified.getId().getName()).isEqualTo("counter");
        assertThat(modified.getId().getTags()).containsExactly(Tag.of("key", "append"), Tag.of("tag", "value"));
        assertThat(modified.getId().getDescription()).isEqualTo("override");
        assertThat(modified.getId().getBaseUnit()).isEqualTo("replace");
    }

}
