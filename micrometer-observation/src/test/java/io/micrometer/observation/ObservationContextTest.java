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
package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link Observation.Context}.
 *
 * @author Jonatan Ivanov
 */
class ObservationContextTest {

    private Observation.Context context;

    @BeforeEach
    void setUp() {
        this.context = new Observation.Context();
    }

    @Test
    void shouldBeEmptyByDefault() {
        assertThat(context.containsKey(String.class)).isFalse();
        assertThat((String) context.get(String.class)).isNull();
    }

    @Test
    void getShouldReturnWhatWasPutPreviously() {
        assertThat(context.put(String.class, "42")).isSameAs(context);
        assertThat(context.containsKey(String.class)).isTrue();
        assertThat((String) context.get(String.class)).isEqualTo("42");

        assertThat(context.put(Integer.class, 123)).isSameAs(context);
        assertThat(context.containsKey(Integer.class)).isTrue();
        assertThat((Integer) context.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void overwrittenValuesShouldBeUpdated() {
        context.put(String.class, "42").put(Integer.class, 123).put(String.class, "24");
        assertThat((String) context.get(String.class)).isEqualTo("24");
        assertThat((Integer) context.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void getOrDefaultShouldUseFallbackValue() {
        context.put(String.class, "42");
        assertThat(context.getOrDefault(String.class, "abc")).isEqualTo("42");
        assertThat(context.getOrDefault(Integer.class, 123)).isEqualTo(123);
    }

    @Test
    void getOrDefaultSupplierWhenKeyIsPresent() {
        context.put(String.class, "42");

        @SuppressWarnings("unchecked")
        Supplier<String> defaultSupplier = mock(Supplier.class);
        when(defaultSupplier.get()).thenReturn("abc");

        assertThat(context.getOrDefault(String.class, defaultSupplier)).isEqualTo("42");
        verifyNoInteractions(defaultSupplier);
    }

    @Test
    void getOrDefaultSupplierWhenKeyIsMissing() {
        context.put(String.class, "42");

        @SuppressWarnings("unchecked")
        Supplier<Integer> defaultSupplier = mock(Supplier.class);
        when(defaultSupplier.get()).thenReturn(123);

        assertThat(context.getOrDefault(Integer.class, defaultSupplier)).isEqualTo(123);
        verify(defaultSupplier, times(1)).get();
    }

    @Test
    void getRequiredShouldFailIfThereIsNoValue() {
        context.put(String.class, "42");
        assertThat((String) context.getRequired(String.class)).isEqualTo("42");
        assertThatThrownBy(() -> context.getRequired(Integer.class)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Context does not have an entry for key [class java.lang.Integer]")
            .hasNoCause();
    }

    @Test
    void computeIfAbsentShouldUseFallbackValue() {
        context.put(String.class, "42");
        assertThat((String) context.computeIfAbsent(String.class, clazz -> "abc")).isEqualTo("42");
        assertThat((String) context.get(String.class)).isEqualTo("42");

        assertThat((Integer) context.computeIfAbsent(Integer.class, clazz -> 123)).isEqualTo(123);
        assertThat((Integer) context.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void removedItemsShouldNotBePresent() {
        context.put(String.class, "42").put(Integer.class, 123).remove(String.class);
        assertThat((Integer) context.get(Integer.class)).isEqualTo(123);
        assertThat((String) context.get(String.class)).isNull();
    }

    @Test
    void removeNonExistingItemShouldNotFail() {
        context.remove(String.class);
    }

    @Test
    void itemsShouldNotBePresentAfterClear() {
        context.put(String.class, "42").put(Integer.class, 123).clear();
        assertThat((Integer) context.get(Integer.class)).isNull();
        assertThat((String) context.get(String.class)).isNull();
    }

    @Test
    void cleanEmptyContextShouldNotFail() {
        context.clear();
    }

    @Test
    void sameKeyShouldOverrideKeyValue() {
        KeyValue low = KeyValue.of("low", "LOW");
        KeyValue high = KeyValue.of("high", "HIGH");
        context.addLowCardinalityKeyValue(low);
        context.addHighCardinalityKeyValue(high);

        assertThat(context.getLowCardinalityKeyValue("low")).isSameAs(low);
        assertThat(context.getHighCardinalityKeyValue("high")).isSameAs(high);

        KeyValue newLow = KeyValue.of("low", "LOW-NEW");
        KeyValue newHigh = KeyValue.of("high", "HIGH-NEW");
        context.addLowCardinalityKeyValue(newLow);
        context.addHighCardinalityKeyValue(newHigh);

        assertThat(context.getLowCardinalityKeyValue("low")).isSameAs(newLow);
        assertThat(context.getHighCardinalityKeyValue("high")).isSameAs(newHigh);
        assertThat(context.getLowCardinalityKeyValues()).containsExactly(newLow);
        assertThat(context.getHighCardinalityKeyValues()).containsExactly(newHigh);
    }

    @Test
    void removingLowCardinalityKeysShouldBePossible() {
        context.addLowCardinalityKeyValues(KeyValues.of(KeyValue.of("key", "VALUE"), KeyValue.of("key2", "VALUE2"),
                KeyValue.of("key3", "VALUE3"), KeyValue.of("key4", "VALUE4")));

        context.removeLowCardinalityKeyValue("key");
        context.removeLowCardinalityKeyValues("key3", "key4");

        assertThat(context.getLowCardinalityKeyValues()).containsExactly(KeyValue.of("key2", "VALUE2"));
    }

    @Test
    void removingHighCardinalityKeysShouldBePossible() {
        context.addHighCardinalityKeyValues(KeyValues.of(KeyValue.of("key", "VALUE"), KeyValue.of("key2", "VALUE2"),
                KeyValue.of("key3", "VALUE3"), KeyValue.of("key4", "VALUE4")));

        context.removeHighCardinalityKeyValue("key");
        context.removeHighCardinalityKeyValues("key3", "key4");

        assertThat(context.getHighCardinalityKeyValues()).containsExactly(KeyValue.of("key2", "VALUE2"));
    }

}
