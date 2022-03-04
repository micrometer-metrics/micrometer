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

import io.micrometer.core.instrument.observation.Observation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation.TagsProvider}.
 *
 * @author Jonatan Ivanov
 */
class TagsProviderTest {
    @Test
    void tagsShouldBeEmptyByDefault() {
        Observation.TagsProvider<Observation.Context> tagsProvider = new TestTagsProvider();

        assertThat(tagsProvider.getLowCardinalityTags(new Observation.Context())).isEmpty();
        assertThat(tagsProvider.getHighCardinalityTags(new Observation.Context())).isEmpty();
    }

    @Test
    void tagsShouldBeMergedIntoCompositeByDefault() {
        Observation.TagsProvider<Observation.Context> tagsProvider = new Observation.TagsProvider.CompositeTagsProvider(
                new MatchingTestTagsProvider(), new AnotherMatchingTestTagsProvider(), new NotMatchingTestTagsProvider()
        );

        assertThat(tagsProvider.getLowCardinalityTags(new Observation.Context())).containsExactlyInAnyOrder(Tag.of("matching-low-1", ""), Tag.of("matching-low-2", ""));
        assertThat(tagsProvider.getHighCardinalityTags(new Observation.Context())).containsExactlyInAnyOrder(Tag.of("matching-high-1", ""), Tag.of("matching-high-2", ""));
    }

    static class TestTagsProvider implements Observation.TagsProvider<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    static class MatchingTestTagsProvider implements Observation.TagsProvider<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public Tags getLowCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("matching-low-1", ""));
        }

        @Override
        public Tags getHighCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("matching-high-1", ""));
        }
    }


    static class AnotherMatchingTestTagsProvider implements Observation.TagsProvider<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public Tags getLowCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("matching-low-2", ""));
        }

        @Override
        public Tags getHighCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("matching-high-2", ""));
        }
    }

    static class NotMatchingTestTagsProvider implements Observation.TagsProvider<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }

        @Override
        public Tags getLowCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("not-matching-low", ""));
        }

        @Override
        public Tags getHighCardinalityTags(Observation.Context context) {
            return Tags.of(Tag.of("not-matching-high", ""));
        }
    }
}
