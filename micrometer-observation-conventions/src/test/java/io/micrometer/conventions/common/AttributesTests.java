/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micrometer.conventions.common.AttributeKey.*;
import static java.util.Collections.singletonList;

/** Unit tests for {@link Attributes}s. */
@SuppressWarnings("rawtypes")
class AttributesTests {

    @Test
    void forEach() {
        Map<AttributeKey, Object> entriesSeen = new LinkedHashMap<>();

        Attributes attributes = Attributes.of(stringKey("key1"), "value1", longKey("key2"), 333L);

        attributes.forEach(entriesSeen::put);

        Assertions.assertThat(entriesSeen).containsExactly(Assertions.entry(stringKey("key1"), "value1"),
                Assertions.entry(longKey("key2"), 333L));
    }

    @Test
    void forEach_singleAttribute() {
        Map<AttributeKey, Object> entriesSeen = new HashMap<>();

        Attributes attributes = Attributes.of(stringKey("key"), "value");
        attributes.forEach(entriesSeen::put);
        Assertions.assertThat(entriesSeen).containsExactly(Assertions.entry(stringKey("key"), "value"));
    }

    @Test
    void putAll() {
        Attributes attributes = Attributes.of(stringKey("key1"), "value1", longKey("key2"), 333L);
        Assertions.assertThat(Attributes.builder().put(booleanKey("key3"), true).putAll(attributes).build())
                .isEqualTo(Attributes.of(stringKey("key1"), "value1", longKey("key2"), 333L, booleanKey("key3"), true));
    }

    @Test
    void putAll_null() {
        Assertions.assertThat(Attributes.builder().put(booleanKey("key3"), true).putAll(null).build())
                .isEqualTo(Attributes.of(booleanKey("key3"), true));
    }

    @SuppressWarnings("CollectionIncompatibleType")
    @Test
    void asMap() {
        Attributes attributes = Attributes.of(stringKey("key1"), "value1", longKey("key2"), 333L);

        Map<AttributeKey<?>, Object> map = attributes.asMap();
        Assertions.assertThat(map).containsExactly(Assertions.entry(stringKey("key1"), "value1"),
                Assertions.entry(longKey("key2"), 333L));

        Assertions.assertThat(map.get(stringKey("key1"))).isEqualTo("value1");
        Assertions.assertThat(map.get(longKey("key2"))).isEqualTo(333L);
        // Map of AttributeKey, not String
        Assertions.assertThat(map.get("key1")).isNull();
        Assertions.assertThat(map.get(null)).isNull();
        Assertions.assertThat(map.keySet()).containsExactlyInAnyOrder(stringKey("key1"), longKey("key2"));
        Assertions.assertThat(map.values()).containsExactlyInAnyOrder("value1", 333L);
        Assertions.assertThat(map.entrySet()).containsExactlyInAnyOrder(Assertions.entry(stringKey("key1"), "value1"),
                Assertions.entry(longKey("key2"), 333L));
        Assertions.assertThat(map.entrySet().contains(Assertions.entry(stringKey("key1"), "value1"))).isTrue();
        Assertions.assertThat(map.entrySet().contains(Assertions.entry(stringKey("key1"), "value2"))).isFalse();
        Assertions.assertThat(map.isEmpty()).isFalse();
        Assertions.assertThat(map.containsKey(stringKey("key1"))).isTrue();
        Assertions.assertThat(map.containsKey(longKey("key2"))).isTrue();
        Assertions.assertThat(map.containsKey(stringKey("key3"))).isFalse();
        Assertions.assertThat(map.containsKey(null)).isFalse();
        Assertions.assertThat(map.containsValue("value1")).isTrue();
        Assertions.assertThat(map.containsValue(333L)).isTrue();
        Assertions.assertThat(map.containsValue("cat")).isFalse();
        Assertions.assertThatThrownBy(() -> map.put(stringKey("animal"), "cat"))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.remove(stringKey("key1")))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.putAll(Collections.emptyMap()))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);

        Assertions.assertThat(map.keySet().contains(stringKey("key1"))).isTrue();
        Assertions.assertThat(map.keySet().contains(stringKey("key3"))).isFalse();
        Assertions.assertThat(map.keySet().containsAll(Arrays.asList(stringKey("key1"), longKey("key2")))).isTrue();
        Assertions.assertThat(map.keySet().containsAll(Arrays.asList(stringKey("key1"), longKey("key3")))).isFalse();
        Assertions.assertThat(map.keySet().containsAll(null)).isFalse();
        Assertions.assertThat(map.keySet().containsAll(Collections.emptyList())).isTrue();
        Assertions.assertThat(map.keySet().size()).isEqualTo(2);
        Assertions.assertThat(map.keySet().toArray()).containsExactlyInAnyOrder(stringKey("key1"), longKey("key2"));
        AttributeKey<?>[] keys = new AttributeKey[2];
        map.keySet().toArray(keys);
        Assertions.assertThat(keys).containsExactlyInAnyOrder(stringKey("key1"), longKey("key2"));
        keys = new AttributeKey[0];
        Assertions.assertThat(map.keySet().toArray(keys)).containsExactlyInAnyOrder(stringKey("key1"), longKey("key2"));
        Assertions.assertThat(keys).isEmpty(); // Didn't use input array.
        Assertions.assertThatThrownBy(() -> map.keySet().iterator().remove())
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThat(map.keySet().containsAll(singletonList(stringKey("key1")))).isTrue();
        Assertions.assertThat(map.keySet().containsAll(Arrays.asList(stringKey("key1"), stringKey("key3")))).isFalse();
        Assertions.assertThat(map.keySet().isEmpty()).isFalse();
        Assertions.assertThatThrownBy(() -> map.keySet().add(stringKey("key3")))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.keySet().remove(stringKey("key1")))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.keySet().addAll(Collections.singletonList(stringKey("key3"))))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.keySet().retainAll(Collections.singletonList(stringKey("key3"))))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.keySet().removeAll(Collections.singletonList(stringKey("key3"))))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(() -> map.keySet().clear()).isInstanceOf(UnsupportedOperationException.class);

        Assertions.assertThat(map.values().contains("value1")).isTrue();
        Assertions.assertThat(map.values().contains("value3")).isFalse();

        Assertions.assertThat(map.toString()).isEqualTo("ReadOnlyArrayMap{key1=value1,key2=333}");

        Map<AttributeKey<?>, Object> emptyMap = Attributes.builder().build().asMap();
        Assertions.assertThat(emptyMap.isEmpty()).isTrue();
        Assertions.assertThatThrownBy(() -> emptyMap.entrySet().iterator().next())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void builder_nullKey() {
        Attributes attributes = Attributes.builder().put(stringKey(null), "value").build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.empty());
    }

    @Test
    void forEach_empty() {
        AtomicBoolean sawSomething = new AtomicBoolean(false);
        Attributes emptyAttributes = Attributes.empty();
        emptyAttributes.forEach((key, value) -> sawSomething.set(true));
        Assertions.assertThat(sawSomething.get()).isFalse();
    }

    @Test
    void orderIndependentEquality() {
        Attributes one = Attributes.of(stringKey("key1"), "value1", stringKey("key2"), "value2");
        Attributes two = Attributes.of(stringKey("key2"), "value2", stringKey("key1"), "value1");

        Assertions.assertThat(one).isEqualTo(two);

        Attributes three = Attributes.of(stringKey("key1"), "value1", stringKey("key2"), "value2", stringKey(""),
                "empty", stringKey("key3"), "value3", stringKey("key4"), "value4");
        Attributes four = Attributes.of(null, "null", stringKey("key2"), "value2", stringKey("key1"), "value1",
                stringKey("key4"), "value4", stringKey("key3"), "value3");

        Assertions.assertThat(three).isEqualTo(four);
    }

    @Test
    void deduplication() {
        Attributes one = Attributes.of(stringKey("key1"), "valueX", stringKey("key1"), "value1");
        Attributes two = Attributes.of(stringKey("key1"), "value1");

        Assertions.assertThat(one).isEqualTo(two);
    }

    @Test
    void deduplication_oddNumberElements() {
        Attributes one = Attributes.builder().put(stringKey("key2"), "valueX").put(stringKey("key2"), "value2")
                .put(stringKey("key1"), "value1").build();
        Attributes two = Attributes.builder().put(stringKey("key2"), "value2").put(stringKey("key1"), "value1").build();

        Assertions.assertThat(one).isEqualTo(two);
    }

    @Test
    void emptyAndNullKey() {
        Attributes noAttributes = Attributes.of(stringKey(""), "empty", null, "null");
        Assertions.assertThat(noAttributes).isSameAs(Attributes.empty());
        noAttributes = Attributes.of(null, "empty", stringKey(""), "null");
        Assertions.assertThat(noAttributes).isSameAs(Attributes.empty());

        Assertions.assertThat(Attributes.of(stringKey("one"), "one", stringKey(""), "null"))
                .isEqualTo(Attributes.of(stringKey("one"), "one"));
    }

    @Test
    void builder() {
        Attributes attributes = Attributes.builder().put("string", "value1").put("long", 100).put(longKey("long2"), 10)
                .put("double", 33.44).put("boolean", "duplicateShouldBeRemoved").put("boolean", false).build();

        Attributes wantAttributes = Attributes.of(stringKey("string"), "value1", longKey("long"), 100L,
                longKey("long2"), 10L, doubleKey("double"), 33.44, booleanKey("boolean"), false);
        Assertions.assertThat(attributes).isEqualTo(wantAttributes);

        AttributesBuilder newAttributes = attributes.toBuilder();
        newAttributes.put("newKey", "newValue");
        Assertions.assertThat(newAttributes.build())
                .isEqualTo(Attributes.of(stringKey("string"), "value1", longKey("long"), 100L, longKey("long2"), 10L,
                        doubleKey("double"), 33.44, booleanKey("boolean"), false, stringKey("newKey"), "newValue"));
        // Original not mutated.
        Assertions.assertThat(attributes).isEqualTo(wantAttributes);
    }

    @Test
    void builderWithAttributeKeyList() {
        Attributes attributes = Attributes.builder().put("string", "value1").put(longKey("long"), 10)
                .put(stringArrayKey("anotherString"), "value1", "value2", "value3")
                .put(longArrayKey("anotherLong"), 10L, 20L, 30L)
                .put(booleanArrayKey("anotherBoolean"), true, false, true).build();

        Attributes wantAttributes = Attributes.of(stringKey("string"), "value1", longKey("long"), 10L,
                stringArrayKey("anotherString"), Arrays.asList("value1", "value2", "value3"),
                longArrayKey("anotherLong"), Arrays.asList(10L, 20L, 30L), booleanArrayKey("anotherBoolean"),
                Arrays.asList(true, false, true));
        Assertions.assertThat(attributes).isEqualTo(wantAttributes);

        AttributesBuilder newAttributes = attributes.toBuilder();
        newAttributes.put("newKey", "newValue");
        Assertions.assertThat(newAttributes.build())
                .isEqualTo(Attributes.of(stringKey("string"), "value1", longKey("long"), 10L,
                        stringArrayKey("anotherString"), Arrays.asList("value1", "value2", "value3"),
                        longArrayKey("anotherLong"), Arrays.asList(10L, 20L, 30L), booleanArrayKey("anotherBoolean"),
                        Arrays.asList(true, false, true), stringKey("newKey"), "newValue"));
        // Original not mutated.
        Assertions.assertThat(attributes).isEqualTo(wantAttributes);
    }

    @Test
    void builder_arrayTypes() {
        Attributes attributes = Attributes.builder().put("string", "value1", "value2", null).put("long", 100L, 200L)
                .put("double", 33.44, -44.33).put("boolean", "duplicateShouldBeRemoved")
                .put(stringKey("boolean"), "true").put("boolean", false, true).build();

        Assertions.assertThat(attributes)
                .isEqualTo(Attributes.of(stringArrayKey("string"), Arrays.asList("value1", "value2", null),
                        longArrayKey("long"), Arrays.asList(100L, 200L), doubleArrayKey("double"),
                        Arrays.asList(33.44, -44.33), booleanArrayKey("boolean"), Arrays.asList(false, true)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_Null() {
        Assertions.assertThat(Attributes.empty().get(stringKey("foo"))).isNull();
        Assertions.assertThat(Attributes.of(stringKey("key"), "value").get(stringKey("foo"))).isNull();
        Assertions.assertThat(Attributes.of(stringKey("key"), "value").get((AttributeKey) null)).isNull();
    }

    @Test
    void get() {
        Assertions.assertThat(Attributes.of(stringKey("key"), "value").get(stringKey("key"))).isEqualTo("value");
        Assertions.assertThat(Attributes.of(stringKey("key"), "value").get(stringKey("value"))).isNull();
        Attributes threeElements = Attributes.of(stringKey("string"), "value", booleanKey("boolean"), true,
                longKey("long"), 1L);
        Assertions.assertThat(threeElements.get(booleanKey("boolean"))).isEqualTo(true);
        Assertions.assertThat(threeElements.get(stringKey("string"))).isEqualTo("value");
        Assertions.assertThat(threeElements.get(longKey("long"))).isEqualTo(1L);
        Attributes twoElements = Attributes.of(stringKey("string"), "value", booleanKey("boolean"), true);
        Assertions.assertThat(twoElements.get(booleanKey("boolean"))).isEqualTo(true);
        Assertions.assertThat(twoElements.get(stringKey("string"))).isEqualTo("value");
        Attributes fourElements = Attributes.of(stringKey("string"), "value", booleanKey("boolean"), true,
                longKey("long"), 1L, stringArrayKey("array"), Arrays.asList("one", "two", "three"));
        Assertions.assertThat(fourElements.get(stringArrayKey("array")))
                .isEqualTo(Arrays.asList("one", "two", "three"));
        Assertions.assertThat(threeElements.get(booleanKey("boolean"))).isEqualTo(true);
        Assertions.assertThat(threeElements.get(stringKey("string"))).isEqualTo("value");
        Assertions.assertThat(threeElements.get(longKey("long"))).isEqualTo(1L);
    }

    @Test
    void toBuilder() {
        Attributes filled = Attributes.builder().put("cat", "meow").put("dog", "bark").build();

        Attributes fromEmpty = Attributes.empty().toBuilder().put("cat", "meow").put("dog", "bark").build();
        Assertions.assertThat(fromEmpty).isEqualTo(filled);
        // Original not mutated.
        Assertions.assertThat(Attributes.empty().isEmpty()).isTrue();

        Attributes partial = Attributes.builder().put("cat", "meow").build();
        Attributes fromPartial = partial.toBuilder().put("dog", "bark").build();
        Assertions.assertThat(fromPartial).isEqualTo(filled);
        // Original not mutated.
        Assertions.assertThat(partial).isEqualTo(Attributes.builder().put("cat", "meow").build());
    }

    @Test
    void nullsAreNoOps() {
        AttributesBuilder builder = Attributes.builder();
        builder.put(stringKey("attrValue"), "attrValue");
        builder.put("string", "string");
        builder.put("long", 10);
        builder.put("double", 1.0);
        builder.put("bool", true);
        builder.put("arrayString", new String[] { "string" });
        builder.put("arrayLong", new long[] { 10L });
        builder.put("arrayDouble", new double[] { 1.0 });
        builder.put("arrayBool", new boolean[] { true });
        Assertions.assertThat(builder.build().size()).isEqualTo(9);

        // note: currently these are no-op calls; that behavior is not required, so if it
        // needs to
        // change, that is fine.
        builder.put(stringKey("attrValue"), null);
        builder.put("string", (String) null);
        builder.put("arrayString", (String[]) null);
        builder.put("arrayLong", (long[]) null);
        builder.put("arrayDouble", (double[]) null);
        builder.put("arrayBool", (boolean[]) null);

        Attributes attributes = builder.build();
        Assertions.assertThat(attributes.size()).isEqualTo(9);
        Assertions.assertThat(attributes.get(stringKey("string"))).isEqualTo("string");
        Assertions.assertThat(attributes.get(stringArrayKey("arrayString"))).isEqualTo(singletonList("string"));
        Assertions.assertThat(attributes.get(longArrayKey("arrayLong"))).isEqualTo(singletonList(10L));
        Assertions.assertThat(attributes.get(doubleArrayKey("arrayDouble"))).isEqualTo(singletonList(1.0d));
        Assertions.assertThat(attributes.get(booleanArrayKey("arrayBool"))).isEqualTo(singletonList(true));
    }

    @Test
    void attributesToString() {
        Attributes attributes = Attributes.builder().put("otel.status_code", "OK").put("http.response_size", 100)
                .put("process.cpu_consumed", 33.44).put("error", true).put("success", "true").build();

        Assertions.assertThat(attributes.toString()).isEqualTo("{error=true, http.response_size=100, "
                + "otel.status_code=\"OK\", process.cpu_consumed=33.44, success=\"true\"}");
    }

    @Test
    void onlySameTypeCanRetrieveValue() {
        Attributes attributes = Attributes.of(stringKey("animal"), "cat");
        Assertions.assertThat(attributes.get(stringKey("animal"))).isEqualTo("cat");
        Assertions.assertThat(attributes.get(longKey("animal"))).isNull();
    }

    @Test
    void remove() {
        AttributesBuilder builder = Attributes.builder();
        Assertions.assertThat(builder.remove(stringKey(""))).isEqualTo(builder);

        Attributes attributes = Attributes.builder().remove(stringKey("key1")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().build());

        attributes = Attributes.builder().put("key1", "value1").build().toBuilder().remove(stringKey("key1"))
                .remove(stringKey("key1")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().build());

        attributes = Attributes.builder().put("key1", "value1").put("key1", "value2").put("key2", "value2")
                .put("key3", "value3").remove(stringKey("key1")).build();
        Assertions.assertThat(attributes)
                .isEqualTo(Attributes.builder().put("key2", "value2").put("key3", "value3").build());

        attributes = Attributes.builder().put("key1", "value1").put("key1", true).remove(stringKey("key1"))
                .remove(stringKey("key1")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().put("key1", true).build());
    }

    @Test
    void removeIf() {
        AttributesBuilder builder = Attributes.builder();
        Assertions.assertThat(builder.removeIf(unused -> true)).isEqualTo(builder);

        Attributes attributes = Attributes.builder().removeIf(key -> key.getKey().equals("key1")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().build());

        attributes = Attributes.builder().put("key1", "value1").build().toBuilder()
                .removeIf(key -> key.getKey().equals("key1")).removeIf(key -> key.getKey().equals("key1")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().build());

        attributes = Attributes.builder().put("key1", "value1").put("key1", "value2").put("key2", "value2")
                .put("key3", "value3").removeIf(key -> key.getKey().equals("key1")).build();
        Assertions.assertThat(attributes)
                .isEqualTo(Attributes.builder().put("key2", "value2").put("key3", "value3").build());

        attributes = Attributes.builder().put("key1", "value1A").put("key1", true)
                .removeIf(key -> key.getKey().equals("key1") && key.getType().equals(AttributeType.STRING)).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().put("key1", true).build());

        attributes = Attributes.builder().put("key1", "value1").put("key2", "value2").put("foo", "bar")
                .removeIf(key -> key.getKey().matches("key.*")).build();
        Assertions.assertThat(attributes).isEqualTo(Attributes.builder().put("foo", "bar").build());
    }

    @Test
    void remove_defaultImplementationDoesNotThrow() {
        AttributesBuilder myAttributesBuilder = new AttributesBuilder() {
            @Override
            public Attributes build() {
                return null;
            }

            @Override
            public <T> AttributesBuilder put(AttributeKey<Long> key, int value) {
                return null;
            }

            @Override
            public <T> AttributesBuilder put(AttributeKey<T> key, T value) {
                return null;
            }

            @Override
            public AttributesBuilder putAll(Attributes attributes) {
                return null;
            }
        };

        Assertions.assertThatCode(() -> myAttributesBuilder.remove(stringKey("foo"))).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> myAttributesBuilder.removeIf(unused -> false)).doesNotThrowAnyException();
    }

}
