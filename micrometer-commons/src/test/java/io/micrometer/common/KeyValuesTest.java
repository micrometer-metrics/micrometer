/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.common;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.JRE;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link KeyValues}.
 *
 * @author Phil Webb
 * @author Maciej Walkowiak
 * @author Jon Schneider
 * @author Johnny Lim
 */
class KeyValuesTest {

    // Should match "Eclipse OpenJ9 VM" and "IBM J9 VM"
    private static final String JAVA_VM_NAME_J9_REGEX = ".*J9 VM$";

    @Test
    void dedup() {
        assertThat(KeyValues.of("k1", "v1", "k2", "v2")).containsExactly(KeyValue.of("k1", "v1"),
                KeyValue.of("k2", "v2"));
        assertThat(KeyValues.of("k1", "v1", "k1", "v2")).containsExactly(KeyValue.of("k1", "v2"));
        assertThat(KeyValues.of("k1", "v1", "k1", "v2", "k3", "v3")).containsExactly(KeyValue.of("k1", "v2"),
                KeyValue.of("k3", "v3"));
        assertThat(KeyValues.of("k1", "v1", "k2", "v2", "k2", "v3")).containsExactly(KeyValue.of("k1", "v1"),
                KeyValue.of("k2", "v3"));
    }

    @Test
    void stream() {
        KeyValues keyValues = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        assertThat(keyValues.stream()).hasSize(2);
    }

    @Test
    void spliterator() {
        KeyValues keyValues = KeyValues.of("k1", "v1", "k2", "v2", "k3", "v4");
        Spliterator<KeyValue> spliterator = keyValues.spliterator();
        assertThat(spliterator).hasCharacteristics(Spliterator.IMMUTABLE, Spliterator.ORDERED, Spliterator.SORTED,
                Spliterator.DISTINCT);
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(3);
    }

    @Test
    void keyValuesHashCode() {
        KeyValues keyValues = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        KeyValues keyValues2 = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        assertThat(keyValues.hashCode()).isEqualTo(keyValues2.hashCode());
    }

    @Test
    void keyValuesToString() {
        KeyValues keyValues = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        assertThat(keyValues.toString()).isEqualTo("[keyValue(k1=v1),keyValue(k2=v2)]");
    }

    @Test
    void keyValuesEquality() {
        KeyValues keyValues = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        KeyValues keyValues2 = KeyValues.of(KeyValue.of("k1", "v1"), KeyValue.of("k2", "v2"));
        assertThat(keyValues).isEqualTo(keyValues2);
    }

    @Test
    void createsListWithSingleKeyValue() {
        Iterable<KeyValue> keyValues = KeyValues.of("k1", "v1");
        assertThat(keyValues).containsExactly(KeyValue.of("k1", "v1"));
    }

    @Test
    void nullKeyValueIterableShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((Iterable<KeyValue>) null)).isSameAs(KeyValues.empty());
    }

    @Test
    void nullKeyValueStringArrayShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((String[]) null)).isSameAs(KeyValues.empty());
    }

    // @Issue("#3851")
    @Test
    void nullKeyValuesShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((String) null)).isSameAs(KeyValues.empty());
    }

    @Test
    void nullKeyValueArrayShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((KeyValue[]) null)).isSameAs(KeyValues.empty());
    }

    // @Issue("#3851")
    @Test
    void nullKeyValueShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((KeyValue) null)).isSameAs(KeyValues.empty());
    }

    @Test
    void emptyKeyValueIterableShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of(new ArrayList<>())).isSameAs(KeyValues.empty());
    }

    @Test
    void emptyKeyValueStringArrayShouldProduceEmptyKeyValues() {
        String[] emptyStrings = {};
        assertThat(KeyValues.of(emptyStrings)).isSameAs(KeyValues.empty());
    }

    @Test
    void emptyKeyValueArrayShouldProduceEmptyKeyValues() {
        KeyValue[] emptyKeyValues = {};
        assertThat(KeyValues.of(emptyKeyValues)).isSameAs(KeyValues.empty());
    }

    @Test
    void concatOnTwoKeyValuesWithSameKeyAreMergedIntoOneKeyValue() {
        Iterable<KeyValue> keyValues = KeyValues.concat(KeyValues.of("k", "v1"), "k", "v2");
        assertThat(keyValues).containsExactly(KeyValue.of("k", "v2"));
    }

    // @Issue("#3851")
    @Test
    void concatWhenKeyValuesAreNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("k", "v1");
        KeyValues concatenated = KeyValues.concat(source, (String) null);
        assertThat(source).isSameAs(concatenated);
    }

    @Test
    void zipOnTwoKeyValuesWithSameKeyAreMergedIntoOneKeyValue() {
        Iterable<KeyValue> keyValues = KeyValues.of("k", "v1", "k", "v2");
        assertThat(keyValues).containsExactly(KeyValue.of("k", "v2"));
    }

    @Test
    void andKeyValueShouldReturnNewInstanceWithAddedKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and("t2", "v2");
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andKeyValuesShouldReturnNewInstanceWithAddedKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and("t2", "v2", "t3", "v3");
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2", "t3", "v3");
    }

    @Test
    void andKeyValuesWhenKeyValuesAreOddShouldThrowException() {
        assertThatThrownBy(() -> KeyValues.empty().and("t1", "v1", "t2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void andKeyValuesStringArrayWhenKeyValuesAreEmptyShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and(new String[0]);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andKeyValuesStringArrayWhenKeyValuesArrayIsNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and((String[]) null);
        assertThat(source).isSameAs(merged);
    }

    // @Issue("#3851")
    @Test
    void andKeyValuesStringArrayWhenKeyValuesAreNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and((String) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andKeyValuesShouldReturnANewInstanceWithKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and(KeyValue.of("t2", "v2"));
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andKeyValuesWhenKeyValuesAreEmptyShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and(new KeyValue[0]);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andKeyValuesWhenKeyValuesArrayIsNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and((KeyValue[]) null);
        assertThat(source).isSameAs(merged);
    }

    // @Issue("#3851")
    @Test
    void andKeyValuesWhenKeyValueIsNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and((KeyValue) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andKeyValuesMultipleTimesShouldWork() {
        KeyValues keyValues = KeyValues.empty().and(KeyValue.of("t1", "v1"));

        KeyValues firstAnd = keyValues.and(KeyValue.of("t1", "v1"));
        assertThat(firstAnd).isEqualTo(keyValues);

        KeyValues secondAnd = firstAnd.and(KeyValue.of("t1", "v1"));
        assertThat(secondAnd).isEqualTo(keyValues);
    }

    @Test
    void andIterableShouldReturnNewInstanceWithKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and(Collections.singleton(KeyValue.of("t2", "v2")));
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void andIterableWhenIterableIsNullShouldReturnCurrentInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and((Iterable<KeyValue>) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    void andWhenAlreadyContainsKeyShouldReplaceValue() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = source.and("t2", "v2", "t1", "v3");
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v3", "t2", "v2");
    }

    @Test
    void iteratorShouldIterateKeyValues() {
        KeyValues keyValues = KeyValues.of("t1", "v1");
        Iterator<KeyValue> iterator = keyValues.iterator();
        assertThat(iterator).toIterable().containsExactly(KeyValue.of("t1", "v1"));
    }

    @Test
    void streamShouldStreamKeyValues() {
        KeyValues keyValues = KeyValues.of("t1", "v1");
        Stream<KeyValue> iterator = keyValues.stream();
        assertThat(iterator).containsExactly(KeyValue.of("t1", "v1"));
    }

    @Test
    void concatIterableShouldReturnNewInstanceWithAddedKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = KeyValues.concat(source, Collections.singleton(KeyValue.of("t2", "v2")));
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    void concatStringsShouldReturnNewInstanceWithAddedKeyValues() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues merged = KeyValues.concat(source, "t2", "v2");
        assertThat(source).isNotSameAs(merged);
        assertKeyValues(source, "t1", "v1");
        assertKeyValues(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    @Deprecated
    void zipShouldReturnNewInstanceWithKeyValues() {
        KeyValues keyValues = KeyValues.of("t1", "v1", "t2", "v2");
        assertKeyValues(keyValues, "t1", "v1", "t2", "v2");
    }

    @Test
    void ofIterableShouldReturnNewInstanceWithKeyValues() {
        KeyValues keyValues = KeyValues.of(Collections.singleton(KeyValue.of("t1", "v1")));
        assertKeyValues(keyValues, "t1", "v1");
    }

    @Test
    void ofIterableWhenIterableIsKeyValuesShouldReturnSameInstance() {
        KeyValues source = KeyValues.of("t1", "v1");
        KeyValues keyValues = KeyValues.of(source);
        assertThat(keyValues).isSameAs(source);
    }

    @Test
    void ofKeyValueShouldReturnNewInstance() {
        KeyValues keyValues = KeyValues.of("t1", "v1");
        assertKeyValues(keyValues, "t1", "v1");
    }

    @Test
    void ofKeyValuesShouldReturnNewInstance() {
        KeyValues keyValues = KeyValues.of("t1", "v1", "t2", "v2");
        assertKeyValues(keyValues, "t1", "v1", "t2", "v2");
    }

    @Test
    void emptyShouldNotContainKeyValues() {
        assertThat(KeyValues.empty().iterator()).isExhausted();
    }

    // gh-3313
    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    @EnabledForJreRange(max = JRE.JAVA_18)
    void andEmptyDoesNotAllocate() {
        andEmptyDoesNotAllocate(0);
    }

    // gh-3313
    // See https://github.com/micrometer-metrics/micrometer/issues/3436
    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    @EnabledIf("java19")
    void andEmptyDoesNotAllocateOnJava19() {
        andEmptyDoesNotAllocate(16);
    }

    static boolean java19() {
        return "19".equals(System.getProperty("java.version"));
    }

    private void andEmptyDoesNotAllocate(int expectedAllocatedBytes) {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        KeyValues keyValues = KeyValues.of("a", "b");
        KeyValues extraKeyValues = KeyValues.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        KeyValues combined = keyValues.and(extraKeyValues);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(combined).isEqualTo(keyValues);
        assertThat(allocatedBytes).isEqualTo(expectedAllocatedBytes);
    }

    // gh-3313
    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    @EnabledForJreRange(max = JRE.JAVA_18)
    void ofEmptyDoesNotAllocate() {
        ofEmptyDoesNotAllocate(0);
    }

    // gh-3313
    // See https://github.com/micrometer-metrics/micrometer/issues/3436
    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    @EnabledIf("java19")
    void ofEmptyDoesNotAllocateOnJava19() {
        ofEmptyDoesNotAllocate(16);
    }

    private void ofEmptyDoesNotAllocate(int expectedAllocatedBytes) {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        KeyValues extraKeyValues = KeyValues.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        KeyValues of = KeyValues.of(extraKeyValues);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(of).isEqualTo(KeyValues.empty());
        assertThat(allocatedBytes).isEqualTo(expectedAllocatedBytes);
    }

    private void assertKeyValues(KeyValues keyValues, String... expectedKeyValues) {
        Iterator<KeyValue> actual = keyValues.iterator();
        Iterator<String> expected = Arrays.asList(expectedKeyValues).iterator();
        while (actual.hasNext()) {
            KeyValue keyValue = actual.next();
            assertThat(keyValue.getKey()).isEqualTo(expected.next());
            assertThat(keyValue.getValue()).isEqualTo(expected.next());
        }
        assertThat(expected.hasNext()).isFalse();
    }

    @Test
    void ofExtractingFromElementsReturnsKeyValues() {
        Map<String, String> map = Map.of("micrometer", "tracing", "can", "trace");
        KeyValues keyValues = KeyValues.of(map.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
        assertThat(keyValues).containsExactlyInAnyOrder(KeyValue.of("micrometer", "tracing"),
                KeyValue.of("can", "trace"));
    }

    @Test
    void andExtractingFromElementsReturnsKeyValues() {
        Map<String, String> map = Map.of("micrometer", "tracing", "can", "trace");
        KeyValues keyValues = KeyValues.of("no", "way").and(map.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
        assertThat(keyValues).containsExactlyInAnyOrder(KeyValue.of("no", "way"), KeyValue.of("micrometer", "tracing"),
                KeyValue.of("can", "trace"));
    }

}
