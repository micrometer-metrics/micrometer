/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TagsProvider}.
 *
 * @author Jonatan Ivanov
 */
class TagsProviderTest {

    @Test
    void tagsShouldBeEmptyByDefault() {
        TagsProvider tagsProvider = new DefaultTestTagsProvider();
        assertThat(tagsProvider.getLowCardinalityTags()).isEmpty();
        assertThat(tagsProvider.getHighCardinalityTags()).isEmpty();
        assertThat(tagsProvider.getAllTags()).isEmpty();
    }

    @Test
    void getAllTagsShouldReturnTheConcatenatedResult() {
        TagsProvider tagsProvider = new CustomTestTagsProvider();
        assertThat(tagsProvider.getAllTags()).containsExactlyInAnyOrder(Tag.of("app.name", "testapp"), Tag.of("user.id", "42"));
    }

    static class DefaultTestTagsProvider implements TagsProvider {
    }

    static class CustomTestTagsProvider implements TagsProvider {
        @Override
        public Tags getLowCardinalityTags() {
            return Tags.of("app.name", "testapp");
        }

        @Override
        public Tags getHighCardinalityTags() {
            return Tags.of("user.id", "42");
        }
    }
}
