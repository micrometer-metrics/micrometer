/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.azuremonitor;

import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureMonitorNamingConventionTest {

    private final AzureMonitorNamingConvention namingConvention = new AzureMonitorNamingConvention();

    @Test
    void testNameContainsDesiredCharacters() {
        assertThat(namingConvention.name("{custom@Metric.1}", Type.GAUGE)).isEqualTo("_custom_Metric_1_");
    }

    @Test
    void testTagKeyContainsDesiredCharacters() {
        assertThat(namingConvention.tagKey("Pc.N@me")).isEqualTo("Pc_N_me");
    }

}
