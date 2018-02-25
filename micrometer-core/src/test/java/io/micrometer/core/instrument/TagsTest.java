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
package io.micrometer.core.instrument;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Phil Webb
 * @author Maciej Walkowiak
 */
class TagsTest {

    @Test
    void createsListWithSingleTag() {
        Iterable<Tag> tags = Tags.of("k1", "v1");
        assertThat(tags).containsExactly(Tag.of("k1", "v1"));
    }

    @Test
    void concatOnTwoTagsWithSameKeyAreMergedIntoOneTag() {
        Iterable<Tag> tags = Tags.concat(Tags.of("k", "v1"), "k", "v2");
        assertThat(tags).containsExactly(Tag.of("k", "v2"));
    }

    @Test
    void zipOnTwoTagsWithSameKeyAreMergedIntoOneTag() {
        Iterable<Tag> tags = Tags.of("k", "v1", "k", "v2");
        assertThat(tags).containsExactly(Tag.of("k", "v2"));
    }

    @Test
    void andKeyValueShouldReturnNewInstanceWithAddedTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2", "v2");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andKeyValuesShouldReturnNewInstanceWithAddedTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2", "v2", "t3", "v3");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2", "t3", "v3");
    }

    @Test
    void andKeyValuesWhenKeyValuesAreOddShouldThrowException() {
        assertThatThrownBy(() -> Tags.empty().and("t1", "v1", "t2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void andKeyValuesWhenKeyValuesAreEmptyShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(new String[0]);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andKeyValuesWhenKeyValuesAreNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((String[]) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andTagsShouldReturnANewInstanceWithTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(Tag.of("t2", "v2"));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andTagsWhenTagsAreEmptyShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(new Tag[0]);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andTagsWhenTagsAreNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((Tag[]) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andIterableShouldReturnNewInstanceWithTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(Collections.singleton(Tag.of("t2", "v2")));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andIterableWhenIterableIsNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((Iterable<Tag>) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andWhenAlreadyContainsKeyShouldReplaceValue() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2", "v2", "t1", "v3");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v3", "t2", "v2");
    }

    @Test
    void iteratorShouldIterateTags() {
        Tags tags = Tags.of("t1", "v1");
        Iterator<Tag> iterator = tags.iterator();
        assertThat(iterator).containsExactly(Tag.of("t1", "v1"));
    }

    @Test
    void streamShouldStreamTags() {
        Tags tags = Tags.of("t1", "v1");
        Stream<Tag> iterator = tags.stream();
        assertThat(iterator).containsExactly(Tag.of("t1", "v1"));
    }

    @Test
    void concatIterableShouldReturnNewInstanceWithAddedTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = Tags.concat(source, Collections.singleton(Tag.of("t2", "v2")));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void concatStringsShouldReturnNewInstanceWithAddedTags() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = Tags.concat(source, "t2", "v2");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    @Deprecated
    void zipShouldReturnNewInstanceWithTags() {
        Tags tags = Tags.of("t1", "v1", "t2", "v2");
        assertTags(tags, "t1", "v1", "t2", "v2");
    }

    @Test
    void ofIterableShouldReturnNewInstanceWithTags() {
        Tags tags = Tags.of(Collections.singleton(Tag.of("t1", "v1")));
        assertTags(tags, "t1", "v1");
    }

    @Test
    void ofIterableWhenIterableIsTagsShouldReturnSameInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags tags = Tags.of(source);
        assertThat(tags).isSameAs(source);
    }

    @Test
    void ofKeyValueShouldReturnNewInstance() {
        Tags tags = Tags.of("t1", "v1");
        assertTags(tags, "t1", "v1");
    }

    @Test
    void ofKeyValuesShouldReturnNewInstance() {
        Tags tags = Tags.of("t1", "v1", "t2", "v2");
        assertTags(tags, "t1", "v1", "t2", "v2");
    }

    @Test
    void emptyShouldNotContainTags() {
        assertThat(Tags.empty().iterator()).isEmpty();
    }

    private void assertTags(Tags tags, String... keyValues) {
        Iterator<Tag> actual = tags.iterator();
        Iterator<String> expected = Arrays.asList(keyValues).iterator();
        while (actual.hasNext()) {
            Tag tag = actual.next();
            assertThat(tag.getKey()).isEqualTo(expected.next());
            assertThat(tag.getValue()).isEqualTo(expected.next());
        }
        assertThat(expected.hasNext()).isFalse();
    }
}
