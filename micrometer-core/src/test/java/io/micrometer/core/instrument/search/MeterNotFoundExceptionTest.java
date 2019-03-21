/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class MeterNotFoundExceptionTest {
    private final List<Tag> tags = Collections.singletonList(Tag.of("k", "v"));

    @Test
    void messageWithNameAndTags() {
        assertThat(new MeterNotFoundException("my.name", tags, Counter.class).getMessage())
                .isEqualTo("Unable to locate a matching meter with name 'my.name' with tags:[k:v] of type io.micrometer.core.instrument.Counter");
    }

    @Test
    void messageWithNoNameAndTags() {
        assertThat(new MeterNotFoundException(null, tags, Counter.class).getMessage())
                .isEqualTo("Unable to locate a matching meter with tags:[k:v] of type io.micrometer.core.instrument.Counter");
    }

    @Test
    void messageWithNoNameAndNoTags() {
        assertThat(new MeterNotFoundException(null, emptyList(), Counter.class).getMessage())
                .isEqualTo("Unable to locate a matching meter of type io.micrometer.core.instrument.Counter");
    }
}