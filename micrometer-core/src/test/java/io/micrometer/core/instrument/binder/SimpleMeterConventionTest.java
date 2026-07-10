/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.binder;

import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class SimpleMeterConventionTest {

    @Test
    void createConventionWithName() {
        MeterConvention<Object> convention = new SimpleMeterConvention<>("my.meter");
        assertThat(convention.getName()).isEqualTo("my.meter");
        assertThat(convention.getTags(new Object())).isEqualTo(Tags.empty());
    }

    @Test
    void createConventionWithNameAndFixedTags() {
        Tags tags = Tags.of("key", "value");
        MeterConvention<Object> convention = new SimpleMeterConvention<>("my.meter", tags);
        assertThat(convention.getName()).isEqualTo("my.meter");
        assertThat(convention.getTags(new Object())).isEqualTo(tags);
    }

    @Test
    void createConventionWithNameAndTagsFunction() {
        Function<String, Tags> tagsFunction = context -> Tags.of("contextKey", context);
        MeterConvention<String> convention = new SimpleMeterConvention<>("my.meter", tagsFunction);
        assertThat(convention.getName()).isEqualTo("my.meter");
        assertThat(convention.getTags("testContext")).isEqualTo(Tags.of("contextKey", "testContext"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullTagsFunctionShouldThrowException() {
        assertThatNullPointerException()
            .isThrownBy(() -> new SimpleMeterConvention<>("my.meter", (Function<String, Tags>) null));
    }

}
