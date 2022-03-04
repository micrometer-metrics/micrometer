/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.observation.Observation;
import io.micrometer.core.instrument.observation.ObservationRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationRegistryAssertTests {

    ObservationRegistry registry = new SimpleMeterRegistry();

    ObservationRegistryAssert registryAssert = new ObservationRegistryAssert(registry);

    @Test
    void assertionErrorThrownWhenRemainingSampleFound() {
        Observation sample = Observation.start("hello", registry);

        try (Observation.Scope ws = sample.openScope()) {
            assertThatThrownBy(() -> registryAssert.doesNotHaveRemainingObservation())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected no current observation in the registry but found one");
        }
    }

    @Test
    void noAssertionErrorThrownWhenNoCurrentSample() {
        assertThatCode(() -> this.registryAssert.doesNotHaveRemainingObservation())
                .doesNotThrowAnyException();
    }

}
