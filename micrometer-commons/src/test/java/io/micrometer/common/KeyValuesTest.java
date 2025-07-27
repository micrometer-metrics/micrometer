/*
 * Copyright 2025 the original author or authors.
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
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
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
 * @author Yoobin Yoon
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
    // While we specify elements should not be null, a single null element is a common
    // enough mistake that we handle and test for it
    @SuppressWarnings("NullAway")
    void nullKeyValuesShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((String) null)).isSameAs(KeyValues.empty());
    }

    @Test
    void nullKeyValueArrayShouldProduceEmptyKeyValues() {
        assertThat(KeyValues.of((KeyValue[]) null)).isSameAs(KeyValues.empty());
    }

    // @Issue("#3851")
    @Test
    // While we specify elements should not be null, a single null element is a common
    // enough mistake that we handle and test for it
    @SuppressWarnings("NullAway")
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
    // While we specify elements should not be null, a single null element is a common
    // enough mistake that we handle and test for it
    @SuppressWarnings("NullAway")
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
    // While we specify elements should not be null, a single null element is a common
    // enough mistake that we handle and test for it
    @SuppressWarnings("NullAway")
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
    // While we specify elements should not be null, a single null element is a common
    // enough mistake that we handle and test for it
    @SuppressWarnings("NullAway")
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
    @DisabledForJreRange(min = JRE.JAVA_19, max = JRE.JAVA_19,
            disabledReason = "https://github.com/micrometer-metrics/micrometer/issues/3436")
    void andEmptyDoesNotAllocate() {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        KeyValues keyValues = KeyValues.of("a", "b");
        KeyValues extraKeyValues = KeyValues.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        KeyValues combined = keyValues.and(extraKeyValues);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(combined).isEqualTo(keyValues);
        assertThat(allocatedBytes).isEqualTo(0);
    }

    // gh-3313
    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
            disabledReason = "Sun ThreadMXBean with allocation counter not available")
    @DisabledForJreRange(min = JRE.JAVA_19, max = JRE.JAVA_19,
            disabledReason = "https://github.com/micrometer-metrics/micrometer/issues/3436")
    void ofEmptyDoesNotAllocate() {
        ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long currentThreadId = Thread.currentThread().getId();
        KeyValues extraKeyValues = KeyValues.empty();

        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);
        KeyValues of = KeyValues.of(extraKeyValues);
        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;

        assertThat(of).isEqualTo(KeyValues.empty());
        assertThat(allocatedBytes).isEqualTo(0);
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

    // gh-6564
    @Test
    void ofSmartKeyNameSingleKey() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.required("color", map -> map.get("color"));
        Map<String, String> product = Map.of("color", "red", "category", "electronics");

        KeyValues result = KeyValues.of(product, colorKey);

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly(KeyValue.of("color", "red"));
    }

    @Test
    void ofSmartKeyNameMultipleKeys() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.required("color", map -> map.get("color"));
        SmartKeyName<Map<String, String>> categoryKey = SmartKeyName.required("category", map -> map.get("category"));
        Map<String, String> product = Map.of("color", "blue", "category", "fashion", "brand", "Nike");

        KeyValues result = KeyValues.of(product, colorKey, categoryKey);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(KeyValue.of("color", "blue"), KeyValue.of("category", "fashion"));
    }

    @Test
    void ofSmartKeyNameWithNullValues() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.withFallback("color", map -> map.get("color"),
                "unknown");
        SmartKeyName<Map<String, String>> categoryKey = SmartKeyName.required("category", map -> map.get("category"));
        SmartKeyName<Map<String, String>> brandKey = SmartKeyName.optional("brand", map -> map.get("brand"));

        Map<String, String> product = Map.of("category", "electronics");

        KeyValues result = KeyValues.of(product, colorKey, categoryKey, brandKey);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(KeyValue.of("color", "unknown"),
                KeyValue.of("category", "electronics"));
    }

    @Test
    void andSmartKeyName() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.required("color", map -> map.get("color"));
        KeyValues base = KeyValues.of("service", "shop");
        Map<String, String> product = Map.of("color", "green");

        KeyValues result = base.and(product, colorKey);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(KeyValue.of("service", "shop"), KeyValue.of("color", "green"));
    }

    @Test
    void smartKeyNameConsistentKeySetDemonstration() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.withFallback("color", map -> map.get("color"),
                "unknown");
        SmartKeyName<Map<String, String>> categoryKey = SmartKeyName.withFallback("category",
                map -> map.get("category"), "general");

        Map<String, String> product1 = Map.of("color", "red", "category", "electronics");
        Map<String, String> product2 = Map.of();

        KeyValues result1 = KeyValues.of(product1, colorKey, categoryKey);
        KeyValues result2 = KeyValues.of(product2, colorKey, categoryKey);

        assertThat(result1.stream().map(KeyValue::getKey)).containsExactlyInAnyOrder("color", "category");
        assertThat(result2.stream().map(KeyValue::getKey)).containsExactlyInAnyOrder("color", "category");

        assertThat(result1).containsExactlyInAnyOrder(KeyValue.of("color", "red"),
                KeyValue.of("category", "electronics"));
        assertThat(result2).containsExactlyInAnyOrder(KeyValue.of("color", "unknown"),
                KeyValue.of("category", "general"));
    }

    @Test
    void smartKeyNameWithNullContextShouldUseFallbacks() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.withFallback("color", map -> map.get("color"),
                "unknown");
        SmartKeyName<Map<String, String>> categoryKey = SmartKeyName.required("category", map -> map.get("category"));

        KeyValues result = KeyValues.of(null, colorKey, categoryKey);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(KeyValue.of("color", "unknown"), KeyValue.of("category", "none"));
    }

    @Test
    void smartKeyNameChaining() {
        SmartKeyName<String> lengthKey = SmartKeyName.required("length", str -> String.valueOf(str.length()));

        KeyValues result = KeyValues.of("service", "api").and("hello", lengthKey).and("request_id", "123");

        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(KeyValue.of("service", "api"), KeyValue.of("length", "5"),
                KeyValue.of("request_id", "123"));
    }

    @Test
    void smartKeyNameAsStringShouldReturnKeyName() {
        SmartKeyName<String> key = SmartKeyName.required("test.key", String::toString);

        assertThat(key.asString()).isEqualTo("test.key");
    }

    @Test
    void smartKeyNameIsRequiredShouldReturnCorrectValue() {
        SmartKeyName<String> requiredKey = SmartKeyName.required("key", String::toString);
        SmartKeyName<String> optionalKey = SmartKeyName.optional("key", String::toString);
        SmartKeyName<String> withFallbackKey = SmartKeyName.withFallback("key", String::toString, "default");

        assertThat(requiredKey.isRequired()).isTrue();
        assertThat(optionalKey.isRequired()).isFalse();
        assertThat(withFallbackKey.isRequired()).isTrue();
    }

    @Test
    void smartKeyNameGetFallbackValueShouldReturnCorrectValue() {
        SmartKeyName<String> withFallback = SmartKeyName.withFallback("key", String::toString, "default");
        SmartKeyName<String> withoutFallback = SmartKeyName.required("key", String::toString);
        SmartKeyName<String> optionalKey = SmartKeyName.optional("key", String::toString);

        assertThat(withFallback.getFallbackValue()).isEqualTo("default");
        assertThat(withoutFallback.getFallbackValue()).isNull();
        assertThat(optionalKey.getFallbackValue()).isNull();
    }

    @Test
    void smartKeyNameRequiredWithNullValueShouldUseFallbackValue() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.required("color", map -> map.get("color"));
        Map<String, String> context = Map.of("region", "us-east");

        KeyValue result = colorKey.valueOf(context);

        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("color");
        assertThat(result.getValue()).isEqualTo(KeyValue.NONE_VALUE);
    }

    @Test
    void smartKeyNameOptionalWithNullValueShouldReturnNull() {
        SmartKeyName<Map<String, String>> colorKey = SmartKeyName.optional("color", map -> map.get("color"));
        Map<String, String> context = Map.of("region", "us-west");

        KeyValue result = colorKey.valueOf(context);

        assertThat(result).isNull();
    }

    @Test
    void smartKeyNameWithFallbackAndNullValueShouldUseCustomFallback() {
        SmartKeyName<Map<String, String>> regionKey = SmartKeyName.withFallback("region", map -> map.get("region"),
                "unknown");
        Map<String, String> context = Map.of("color", "green");

        KeyValue result = regionKey.valueOf(context);

        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("region");
        assertThat(result.getValue()).isEqualTo("unknown");
    }

    @Test
    void smartKeyNameWithNullContextShouldHandleGracefully() {
        SmartKeyName<Map<String, String>> requiredKey = SmartKeyName.required("key", map -> map.get("key"));
        SmartKeyName<Map<String, String>> optionalKey = SmartKeyName.optional("key", map -> map.get("key"));
        SmartKeyName<Map<String, String>> fallbackKey = SmartKeyName.withFallback("key", map -> map.get("key"),
                "default");

        KeyValue requiredResult = requiredKey.valueOf(null);
        KeyValue optionalResult = optionalKey.valueOf(null);
        KeyValue fallbackResult = fallbackKey.valueOf(null);

        assertThat(requiredResult).isNotNull();
        assertThat(requiredResult.getValue()).isEqualTo(KeyValue.NONE_VALUE);

        assertThat(optionalResult).isNull();

        assertThat(fallbackResult).isNotNull();
        assertThat(fallbackResult.getValue()).isEqualTo("default");
    }

}
