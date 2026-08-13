/*
 * Copyright 2026 VMware, Inc.
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
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation.Event} and {@link SimpleEvent}.
 */
class ObservationEventTests {

    @Test
    void defaultGetKeyValuesReturnsEmptyKeyValues() {
        Observation.Event customEvent = new CustomEvent("test");
        assertThat(customEvent.getLowCardinalityKeyValues()).isSameAs(KeyValues.empty());
        assertThat(customEvent.getHighCardinalityKeyValues()).isSameAs(KeyValues.empty());
        assertThat(customEvent.getKeyValues()).isSameAs(KeyValues.empty());
    }

    @Test
    void eventOfNameHasDefaultKeyValuesAndWallTime() {
        long before = System.currentTimeMillis();
        Observation.Event event = Observation.Event.of("myEvent");
        long after = System.currentTimeMillis();

        assertThat(event.getName()).isEqualTo("myEvent");
        assertThat(event.getContextualName()).isEqualTo("myEvent");
        assertThat(event.getWallTime()).isBetween(before, after);
        assertThat(event.getLowCardinalityKeyValues()).isEmpty();
        assertThat(event.getHighCardinalityKeyValues()).isEmpty();
        assertThat(event.getKeyValues()).isEmpty();
    }

    @Test
    void eventOfNameAndContextualNameHasDefaultKeyValues() {
        long before = System.currentTimeMillis();
        Observation.Event event = Observation.Event.of("myEvent", "contextual %s");
        long after = System.currentTimeMillis();

        assertThat(event.getName()).isEqualTo("myEvent");
        assertThat(event.getContextualName()).isEqualTo("contextual %s");
        assertThat(event.getWallTime()).isBetween(before, after);
        assertThat(event.getLowCardinalityKeyValues()).isEmpty();
        assertThat(event.getHighCardinalityKeyValues()).isEmpty();
        assertThat(event.getKeyValues()).isEmpty();
    }

    @Test
    void eventOfNameContextualNameAndWallTimeHasDefaultKeyValues() {
        Observation.Event event = Observation.Event.of("myEvent", "contextual", 123456L);

        assertThat(event.getName()).isEqualTo("myEvent");
        assertThat(event.getContextualName()).isEqualTo("contextual");
        assertThat(event.getWallTime()).isEqualTo(123456L);
        assertThat(event.getLowCardinalityKeyValues()).isEmpty();
        assertThat(event.getHighCardinalityKeyValues()).isEmpty();
        assertThat(event.getKeyValues()).isEmpty();
    }

    @Test
    void eventWithHighCardinalityKeyValues() {
        KeyValues highKeyValues = KeyValues.of("hk1", "hv1", "hk2", "hv2");
        Observation.Event event = Observation.Event.of("myEvent", "contextual", 123456L, highKeyValues);

        assertThat(event.getName()).isEqualTo("myEvent");
        assertThat(event.getContextualName()).isEqualTo("contextual");
        assertThat(event.getWallTime()).isEqualTo(123456L);
        assertThat(event.getLowCardinalityKeyValues()).isEmpty();
        assertThat(event.getHighCardinalityKeyValues()).containsExactlyElementsOf(highKeyValues);
        assertThat(event.getKeyValues()).containsExactlyElementsOf(highKeyValues);
    }

    @Test
    void eventWithHighAndLowCardinalityKeyValues() {
        KeyValues highKeyValues = KeyValues.of("hk1", "hv1");
        KeyValues lowKeyValues = KeyValues.of("lk1", "lv1");
        Observation.Event event = Observation.Event.of("myEvent", "contextual", 123456L, highKeyValues, lowKeyValues);

        assertThat(event.getName()).isEqualTo("myEvent");
        assertThat(event.getContextualName()).isEqualTo("contextual");
        assertThat(event.getWallTime()).isEqualTo(123456L);
        assertThat(event.getHighCardinalityKeyValues()).containsExactlyElementsOf(highKeyValues);
        assertThat(event.getLowCardinalityKeyValues()).containsExactlyElementsOf(lowKeyValues);
        assertThat(event.getKeyValues()).containsExactly(KeyValue.of("hk1", "hv1"), KeyValue.of("lk1", "lv1"));
    }

    @Test
    void eventWithIterableKeyValues() {
        Iterable<KeyValue> keyValues = Collections.singletonList(KeyValue.of("k1", "v1"));
        Observation.Event event = Observation.Event.of("myEvent", "contextual", 123456L, keyValues);

        assertThat(event.getHighCardinalityKeyValues()).containsExactly(KeyValue.of("k1", "v1"));
        assertThat(event.getKeyValues()).containsExactly(KeyValue.of("k1", "v1"));
    }

    @Test
    void formatPreservesWallTimeAndKeyValues() {
        KeyValues highKeyValues = KeyValues.of("detail", "timeout");
        KeyValues lowKeyValues = KeyValues.of("attempt", "2");
        Observation.Event event = Observation.Event.of("retry", "attempt %s failed", 9999L, highKeyValues,
                lowKeyValues);

        Observation.Event formatted = event.format(2);

        assertThat(formatted.getName()).isEqualTo("retry");
        assertThat(formatted.getContextualName()).isEqualTo("attempt 2 failed");
        assertThat(formatted.getWallTime()).isEqualTo(9999L);
        assertThat(formatted.getHighCardinalityKeyValues()).containsExactlyElementsOf(highKeyValues);
        assertThat(formatted.getLowCardinalityKeyValues()).containsExactlyElementsOf(lowKeyValues);
        assertThat(formatted.getKeyValues()).containsExactlyElementsOf(lowKeyValues.and(highKeyValues));
    }

    @Test
    void toStringExcludesKeyValues() {
        Observation.Event event = Observation.Event.of("myEvent", "contextual", 123456L, KeyValues.of("k1", "v1"));

        assertThat(event.toString())
            .isEqualTo("event.name='myEvent', event.contextualName='contextual', event.wallTime=123456");
    }

    @NullMarked
    private static class CustomEvent implements Observation.Event {

        private final String name;

        CustomEvent(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

    }

}
