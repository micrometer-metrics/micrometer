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
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.GlobalObservationConvention;
import io.micrometer.observation.Observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry.OverridingObservationConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservationRegistry.OverridingObservationConvention}
 *
 * @author Tadaya Tsuyukubo
 */
class OverridingObservationConventionTests {

    @Test
    void withOverridingConventions() {
        MyGlobalObservationConvention base = new MyGlobalObservationConvention();
        MyObservationConvention first = new MyObservationConvention("first-");
        MyObservationConvention second = new MyObservationConvention("second-");
        OverridingObservationConvention<MyContext> overridingConvention = new ObservationRegistry.OverridingObservationConvention<>(
                base, first, second);

        MyContext context = new MyContext();

        assertThat(overridingConvention.getName()).isEqualTo("second-mine");
        assertThat(overridingConvention.getContextualName(context)).isEqualTo("second-mine-contextual");
        assertThat(overridingConvention.getLowCardinalityKeyValues(context)).extracting(KeyValue::getKey)
                .containsExactlyInAnyOrder("global-low", "first-low", "second-low", "shared");
        assertThat(overridingConvention.getLowCardinalityKeyValues(context))
                .contains(KeyValue.of("shared", "second-LOW"));
        assertThat(overridingConvention.getHighCardinalityKeyValues(context)).extracting(KeyValue::getKey)
                .containsExactlyInAnyOrder("global-high", "first-high", "second-high", "shared");
        assertThat(overridingConvention.getHighCardinalityKeyValues(context))
                .contains(KeyValue.of("shared", "second-HIGH"));
    }

    @Test
    void onlyGlobalConvention() {
        MyGlobalObservationConvention base = new MyGlobalObservationConvention();
        OverridingObservationConvention<MyContext> overridingConvention = new ObservationRegistry.OverridingObservationConvention<>(
                base);
        MyContext context = new MyContext();
        assertThat(overridingConvention.getName()).isEqualTo("global");
        assertThat(overridingConvention.getContextualName(context)).isEqualTo("global-contextual");
        assertThat(overridingConvention.getLowCardinalityKeyValues(context))
                .containsExactlyInAnyOrder(KeyValue.of("global-low", "LOW"), KeyValue.of("shared", "global-LOW"));
        assertThat(overridingConvention.getHighCardinalityKeyValues(context))
                .containsExactlyInAnyOrder(KeyValue.of("global-high", "LOW"), KeyValue.of("shared", "global-high"));
    }

    @Test
    void supportContext() {
        ObservationConvention<MyContext> trueConvention = context -> true;
        ObservationConvention<MyContext> falseConvention = context -> false;

        MyGlobalObservationConvention base = new MyGlobalObservationConvention();
        OverridingObservationConvention<MyContext> overridingConvention;
        MyContext context = new MyContext();

        overridingConvention = new ObservationRegistry.OverridingObservationConvention<>(base, trueConvention,
                trueConvention);
        assertThat(overridingConvention.supportsContext(context)).isTrue();

        overridingConvention = new ObservationRegistry.OverridingObservationConvention<>(base, falseConvention,
                trueConvention);
        assertThat(overridingConvention.supportsContext(context)).isFalse();
    }

    static class MyContext extends Observation.Context {

    }

    static class MyGlobalObservationConvention implements GlobalObservationConvention<MyContext> {

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof MyContext;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(MyContext context) {
            return KeyValues.of(KeyValue.of("global-low", "LOW"), KeyValue.of("shared", "global-LOW"));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(MyContext context) {
            return KeyValues.of(KeyValue.of("global-high", "LOW"), KeyValue.of("shared", "global-high"));
        }

        @Override
        public String getName() {
            return "global";
        }

        @Override
        public String getContextualName(MyContext context) {
            return "global-contextual";
        }

    }

    static class MyObservationConvention implements ObservationConvention<MyContext> {

        private final String prefix;

        public MyObservationConvention(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof MyContext;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(MyContext context) {
            return KeyValues.of(KeyValue.of(prefix + "low", prefix + "LOW"), KeyValue.of("shared", prefix + "LOW"));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(MyContext context) {
            return KeyValues.of(KeyValue.of(prefix + "high", prefix + "HIGH"), KeyValue.of("shared", prefix + "HIGH"));
        }

        @Override
        public String getName() {
            return prefix + "mine";
        }

        @Override
        public String getContextualName(MyContext context) {
            return prefix + "mine-contextual";
        }

    }

}
