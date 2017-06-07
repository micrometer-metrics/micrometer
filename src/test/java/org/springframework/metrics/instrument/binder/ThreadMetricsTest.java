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
import org.springframework.metrics.instrument.Gauge;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ThreadMetricsTest {
    @Test
    void threadMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new ThreadMetrics().bindTo(registry);

        assertThat(registry.findMeter(Gauge.class, "threads_live"))
                .hasValueSatisfying(g -> assertThat(g.value()).isGreaterThan(0));
        assertThat(registry.findMeter(Gauge.class, "threads_daemon"))
                .hasValueSatisfying(g -> assertThat(g.value()).isGreaterThan(0));
        assertThat(registry.findMeter(Gauge.class, "threads_peak"))
                .hasValueSatisfying(g ->  assertThat(g.value()).isGreaterThan(0));
    }
}
