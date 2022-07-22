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
 * Tests for {@link Observation.ObservationConvention}.
 *
 * @author Jonatan Ivanov
 */
class ObservationConventionTest {

    @Test
    void tagsShouldBeEmptyByDefault() {
        Observation.ObservationConvention<Observation.Context> ObservationConvention = new TestObservationConvention();

        assertThat(ObservationConvention.getLowCardinalityKeyValues(new Observation.Context())).isEmpty();
        assertThat(ObservationConvention.getHighCardinalityKeyValues(new Observation.Context())).isEmpty();
    }

    static class TestObservationConvention implements Observation.ObservationConvention<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class MatchingTestObservationConvention implements Observation.ObservationConvention<Observation.Context> {

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

    static class AnotherMatchingTestObservationConvention
            implements Observation.ObservationConvention<Observation.Context> {

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

    static class NotMatchingTestObservationConvention
            implements Observation.ObservationConvention<Observation.Context> {

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
