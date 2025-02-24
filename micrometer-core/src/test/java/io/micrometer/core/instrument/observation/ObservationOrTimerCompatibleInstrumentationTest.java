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
package io.micrometer.core.instrument.observation;

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationOrTimerCompatibleInstrumentationTest {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    @Test
    void noObservationRegistry() {
        ObservationOrTimerCompatibleInstrumentation.start(meterRegistry, null, null, null, null)
            .stop("my.timer", "timer description", () -> Tags.of("a", "b"));
        assertThat(observationRegistry).doesNotHaveAnyObservation();
        Timer timer = meterRegistry.get("my.timer").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getDescription()).isEqualTo("timer description");
        assertThat(timer.getId().getTags()).containsOnly(Tag.of("a", "b"));
    }

    @Test
    void withObservationRegistry() {
        ObservationOrTimerCompatibleInstrumentation
            .start(meterRegistry, observationRegistry, Observation.Context::new, null, TestDefaultConvention.INSTANCE)
            .stop("my.timer", "timer description", () -> Tags.of("a", "b"));
        assertThat(meterRegistry.find("my.timer").timer()).isNull();
        assertThat(observationRegistry).hasSingleObservationThat()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasNameEqualTo("my.observation")
            .hasContextualNameEqualTo("observation ()")
            .hasOnlyKeys("low", "high")
            .hasLowCardinalityKeyValue("low", "value")
            .hasHighCardinalityKeyValue("high", "value");
    }

    private static class TestDefaultConvention implements ObservationConvention<Observation.Context> {

        private static final TestDefaultConvention INSTANCE = new TestDefaultConvention();

        private TestDefaultConvention() {
        }

        @Override
        public String getName() {
            return "my.observation";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low", "value");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("high", "value");
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "observation ()";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

}
