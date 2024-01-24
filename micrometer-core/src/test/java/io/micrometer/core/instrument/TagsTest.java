/*
 * Copyright 2017 VMware, Inc.
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

import com.sun.management.ThreadMXBean;
import io.micrometer.core.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Tags}.
 *
 * @author Phil Webb
 * @author Maciej Walkowiak
 * @author Jon Schneider
 * @author Johnny Lim
 */
class TagsTest {

    // Should match "Eclipse OpenJ9 VM" and "IBM J9 VM"
    private static final String JAVA_VM_NAME_J9_REGEX = ".*J9 VM$";

    @Test
    void dedup() {
        assertThat(Tags.of("k1", "v1", "k2", "v2")).containsExactly(Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        assertThat(Tags.of("k1", "v1", "k1", "v2")).containsExactly(Tag.of("k1", "v2"));
        assertThat(Tags.of("k1", "v1", "k1", "v2", "k3", "v3")).containsExactly(Tag.of("k1", "v2"), Tag.of("k3", "v3"));
        assertThat(Tags.of("k1", "v1", "k2", "v2", "k2", "v3")).containsExactly(Tag.of("k1", "v1"), Tag.of("k2", "v3"));
    }

    @Test
    void stream() {
        Tags tags = Tags.of(Tag.of("k1", "v1"), Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        assertThat(tags.stream()).hasSize(2);
    }

    @Test
    void spliterator() {
        Tags tags = Tags.of("k1", "v1", "k2", "v2", "k3", "v4");
        Spliterator<Tag> spliterator = tags.spliterator();
        assertThat(spliterator).hasCharacteristics(Spliterator.IMMUTABLE, Spliterator.ORDERED, Spliterator.SORTED,
                Spliterator.DISTINCT);
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(3);
    }

    @Test
    void tagsHashCode() {
        Tags tags = Tags.of(Tag.of("k1", "v1"), Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        Tags tags2 = Tags.of(Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        assertThat(tags.hashCode()).isEqualTo(tags2.hashCode());
    }

    @Test
    void tagsToString() {
        Tags tags = Tags.of(Tag.of("k1", "v1"), Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        assertThat(tags.toString()).isEqualTo("[tag(k1=v1),tag(k2=v2)]");
    }

    @Test
    void tagsEquality() {
        Tags tags = Tags.of(Tag.of("k1", "v1"), Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        Tags tags2 = Tags.of(Tag.of("k1", "v1"), Tag.of("k2", "v2"));
        assertThat(tags).isEqualTo(tags2);
    }

    @Test
    void createsListWithSingleTag() {
        Iterable<Tag> tags = Tags.of("k1", "v1");
        assertThat(tags).containsExactly(Tag.of("k1", "v1"));
    }

    @Test
    void nullTagIterableShouldProduceEmptyTags() {
        assertThat(Tags.of((Iterable<Tag>) null)).isSameAs(Tags.empty());
    }

    @Test
    void nullKeyValueArrayShouldProduceEmptyTags() {
        assertThat(Tags.of((String[]) null)).isSameAs(Tags.empty());
    }

    @Issue("#3851")
    @Test
    void nullKeyValuesShouldProduceEmptyTags() {
        assertThat(Tags.of((String) null)).isSameAs(Tags.empty());
    }

    @Test
    void nullTagArrayShouldProduceEmptyTags() {
        assertThat(Tags.of((Tag[]) null)).isSameAs(Tags.empty());
    }

    @Issue("#3851")
    @Test
    void nullTagShouldProduceEmptyTags() {
        assertThat(Tags.of((Tag) null)).isSameAs(Tags.empty());
    }

    @Test
    void emptyTagIterableShouldProduceEmptyTags() {
        assertThat(Tags.of(new ArrayList<>())).isSameAs(Tags.empty());
    }

    @Test
    void emptyKeyValueArrayShouldProduceEmptyTags() {
        String[] emptyStrings = {};
        assertThat(Tags.of(emptyStrings)).isSameAs(Tags.empty());
    }

    @Test
    void emptyTagArrayShouldProduceEmptyTags() {
        Tag[] emptyTags = {};
        assertThat(Tags.of(emptyTags)).isSameAs(Tags.empty());
    }

    @Test
    void concatOnTwoTagsWithSameKeyAreMergedIntoOneTag() {
        Iterable<Tag> tags = Tags.concat(Tags.of("k", "v1"), "k", "v2");
        assertThat(tags).containsExactly(Tag.of("k", "v2"));
    }

    @Issue("#3851")
    @Test
    void concatWhenKeyValuesAreNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("k", "v1");
        Tags concatenated = Tags.concat(source, (String) null);
        assertThat(source).isSameAs(concatenated);
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
    void andKeyValuesWhenKeyValuesArrayIsNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((String[]) null);
        assertThat(source).isSameAs(merged);
    }

    @Issue("#3851")
    @Test
    void andKeyValuesWhenKeyValuesAreNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((String) null);
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

    @Issue("#3851")
    @Test
    void andTagsWhenTagIsNullShouldReturnCurrentInstance() {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((Tag) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andTagsMultipleTimesShouldWork() {
        Tags tags = Tags.empty().and(Tag.of("t1", "v1"));

        Tags firstAnd = tags.and(Tag.of("t1", "v1"));
        assertThat(firstAnd).isEqualTo(tags);

        Tags secondAnd = firstAnd.and(Tag.of("t1", "v1"));
        assertThat(secondAnd).isEqualTo(tags);
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
        assertThat(iterator).toIterable().containsExactly(Tag.of("t1", "v1"));
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
        assertThat(Tags.empty().iterator()).isExhausted();
    }

    @Test
    @Issue("#3313")
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    void andEmptyDoesNotAllocate() {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        Tags tags = Tags.of("a", "b");
        Tags extraTags = Tags.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        Tags combined = tags.and(extraTags);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(combined).isEqualTo(tags);
        assertThat(allocatedBytes).isEqualTo(0);
    }

    @Test
    @Issue("#3313")
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    void ofEmptyDoesNotAllocate() {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        Tags extraTags = Tags.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        Tags of = Tags.of(extraTags);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(of).isEqualTo(Tags.empty());
        assertThat(allocatedBytes).isEqualTo(0);
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
