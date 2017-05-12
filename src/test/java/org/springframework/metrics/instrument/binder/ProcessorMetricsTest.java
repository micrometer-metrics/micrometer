/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.springframework.metrics.instrument.Assertions.assertGaugeValue;

class ProcessorMetricsTest {
    @Test
    void cpuMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new ProcessorMetrics());

        assertGaugeValue(registry, "cpu_total", v -> v > 0);
        assertGaugeValue(registry, "cpu_load_average", v -> v > 0);
    }
}
