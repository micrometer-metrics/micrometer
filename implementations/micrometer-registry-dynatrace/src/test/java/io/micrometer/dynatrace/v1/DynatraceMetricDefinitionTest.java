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
package io.micrometer.dynatrace.v1;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DynatraceMetricDefinitionTest {

    private final String[] technologyTypes = { "java" };

    @Test
    void usesMetricIdAsDescriptionWhenDescriptionIsNotAvailable() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", null, null, null,
                technologyTypes, null);

        assertThat(metric.asJson()).isEqualTo("{\"displayName\":\"custom:test.metric\",\"types\":[\"java\"]}");
    }

    @Test
    void escapesStringsInDescription() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric",
                "The /\"recent cpu usage\" for the Java Virtual Machine process", null, null, technologyTypes, null);

        assertThat(metric.asJson()).isEqualTo(
                "{\"displayName\":\"The /\\\"recent cpu usage\\\" for the Java Virtual Machine process\",\"types\":[\"java\"]}");
    }

    @Test
    void addsUnitWhenAvailable() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", "my test metric",
                DynatraceMetricDefinition.DynatraceUnit.Count, null, technologyTypes, null);

        assertThat(metric.asJson())
            .isEqualTo("{\"displayName\":\"my test metric\",\"unit\":\"Count\",\"types\":[\"java\"]}");
    }

    @Test
    void addsDimensionsWhenAvailable() {
        final Set<String> dimensions = new HashSet<>();
        dimensions.add("first");
        dimensions.add("second");
        dimensions.add("unknown");
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", "my test metric",
                null, dimensions, technologyTypes, null);

        assertThat(metric.asJson()).isEqualTo(
                "{\"displayName\":\"my test metric\",\"dimensions\":[\"first\",\"second\",\"unknown\"],\"types\":[\"java\"]}");
    }

    @Test
    void addsGroupWhenAvailable() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", "my test metric",
                DynatraceMetricDefinition.DynatraceUnit.Count, null, technologyTypes, "my test group");
        assertThat(metric.asJson()).isEqualTo(
                "{\"displayName\":\"my test metric\",\"group\":\"my test group\",\"unit\":\"Count\",\"types\":[\"java\"]}");
    }

}
