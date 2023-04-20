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
package io.micrometer.common;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.docs.Type;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypedKeyValueTest {

    private static final KeyName KEY = () -> "key";

    @Test
    void testStringAttribute() {
        KeyValue keyValue1 = KeyValue.of(KEY.asString(), "value");
        KeyValue keyValue2 = KeyValue.of(() -> "key", "value");
        KeyValue keyValue3 = KEY.withValue("value");

        assertEquals(keyValue1, keyValue2);
        assertEquals(keyValue2, keyValue3);

        assertEquals(KEY.asString(), keyValue1.getKey());
        assertEquals(Type.STRING, keyValue1.getType());
        assertEquals("value", keyValue1.getTypedValue());
    }

    @Test
    void testLongAttribute() {
        KeyValue keyValue1 = KeyValue.of(KEY.asString(), 2);
        KeyValue keyValue2 = KeyValue.of(() -> "key", 2);
        KeyValue keyValue3 = KEY.withValue(2);

        assertEquals(keyValue1, keyValue2);
        assertEquals(keyValue2, keyValue3);

        assertEquals(KEY.asString(), keyValue1.getKey());
        assertEquals(Type.LONG, keyValue1.getType());
        assertEquals(2L, keyValue1.getTypedValue());
    }

    @Test
    void testDoubleAttribute() {
        KeyValue keyValue1 = KeyValue.of(KEY.asString(), 2.2);
        KeyValue keyValue2 = KeyValue.of(() -> "key", 2.2);
        KeyValue keyValue3 = KEY.withValue(2.2);

        assertEquals(keyValue1, keyValue2);
        assertEquals(keyValue2, keyValue3);

        assertEquals(KEY.asString(), keyValue1.getKey());
        assertEquals(Type.DOUBLE, keyValue1.getType());
        assertEquals(2.2D, keyValue1.getTypedValue());
    }

    @Test
    void testBooleanAttribute() {
        KeyValue keyValue1 = KeyValue.of(KEY.asString(), true);
        KeyValue keyValue2 = KeyValue.of(() -> "key", true);
        KeyValue keyValue3 = KEY.withValue(true);

        assertEquals(keyValue1, keyValue2);
        assertEquals(keyValue2, keyValue3);

        assertEquals(KEY.asString(), keyValue1.getKey());
        assertEquals(Type.BOOLEAN, keyValue1.getType());
        assertEquals(true, keyValue1.getTypedValue());
    }

    @Test
    void testPotentialUseCase() {
        Span span = Mockito.mock(Span.class);

        KeyValues keyValues = KeyValues.of(KeyValue.of("string", "string"), KeyValue.of("double", 2.5),
                KeyValue.of("long", 2), KeyValue.of("boolean", true));

        for (KeyValue keyValue : keyValues) {
            switch (keyValue.getType()) {
                case STRING:
                    span.tag(keyValue.getKey(), (String) keyValue.getTypedValue());
                    break;
                case LONG:
                    span.tag(keyValue.getKey(), (long) keyValue.getTypedValue());
                    break;
                case DOUBLE:
                    span.tag(keyValue.getKey(), (double) keyValue.getTypedValue());
                    break;
                case BOOLEAN:
                    span.tag(keyValue.getKey(), (boolean) keyValue.getTypedValue());
                    break;
                default:
                    throw new RuntimeException("Unknown type");
            }
        }
    }

    interface Span {

        void tag(String key, String value);

        void tag(String key, double value);

        void tag(String key, long value);

        void tag(String key, boolean value);

    }

}
