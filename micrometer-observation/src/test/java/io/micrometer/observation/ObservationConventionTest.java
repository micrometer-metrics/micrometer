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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservationConvention}.
 *
 * @author Jonatan Ivanov
 */
class ObservationConventionTest {

    @Test
    void keyValuesShouldBeEmptyByDefault() {
        ObservationConvention<Observation.Context> observationConvention = new TestObservationConvention();

        assertThat(observationConvention.getLowCardinalityKeyValues(new Observation.Context())).isEmpty();
        assertThat(observationConvention.getHighCardinalityKeyValues(new Observation.Context())).isEmpty();
    }

    static class TestObservationConvention implements ObservationConvention<Observation.Context> {

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

}
