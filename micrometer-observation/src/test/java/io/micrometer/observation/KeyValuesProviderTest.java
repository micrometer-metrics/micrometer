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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation.KeyValuesProvider}.
 *
 * @author Jonatan Ivanov
 */
class KeyValuesProviderTest {

    @Test
    void tagsShouldBeEmptyByDefault() {
        Observation.KeyValuesProvider<Observation.Context> keyValuesProvider = new TestKeyValuesProvider();

        assertThat(keyValuesProvider.getLowCardinalityKeyValues(new Observation.Context())).isEmpty();
        assertThat(keyValuesProvider.getHighCardinalityKeyValues(new Observation.Context())).isEmpty();
    }

    @Test
    void tagsShouldBeMergedIntoCompositeByDefault() {
        Observation.KeyValuesProvider<Observation.Context> keyValuesProvider = new Observation.KeyValuesProvider.CompositeKeyValuesProvider(
                new MatchingTestKeyValuesProvider(), new AnotherMatchingTestKeyValuesProvider(),
                new NotMatchingTestKeyValuesProvider());

        assertThat(keyValuesProvider.getLowCardinalityKeyValues(new Observation.Context()))
                .containsExactlyInAnyOrder(KeyValue.of("matching-low-1", ""), KeyValue.of("matching-low-2", ""));
        assertThat(keyValuesProvider.getHighCardinalityKeyValues(new Observation.Context()))
                .containsExactlyInAnyOrder(KeyValue.of("matching-high-1", ""), KeyValue.of("matching-high-2", ""));
    }

    static class TestKeyValuesProvider implements Observation.KeyValuesProvider<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class MatchingTestKeyValuesProvider implements Observation.KeyValuesProvider<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("matching-low-1", ""));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("matching-high-1", ""));
        }

    }

    static class AnotherMatchingTestKeyValuesProvider implements Observation.KeyValuesProvider<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("matching-low-2", ""));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("matching-high-2", ""));
        }

    }

    static class NotMatchingTestKeyValuesProvider implements Observation.KeyValuesProvider<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("not-matching-low", ""));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(KeyValue.of("not-matching-high", ""));
        }

    }

}
