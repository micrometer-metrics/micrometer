/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class JvmGcMetricsTest {

    @Test
    void jvmGcMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        try (JvmGcMetrics jvmGcMetrics = new JvmGcMetrics()) {
            jvmGcMetrics.bindTo(registry);

            assertThat(registry.get("jvm.gc.max.data.size").gauge().value()).isGreaterThanOrEqualTo(0);
            assertThat(registry.get("jvm.gc.live.data.size").gauge().value()).isGreaterThanOrEqualTo(0);
            assertThat(registry.get("jvm.gc.memory.promoted").counter().count()).isGreaterThanOrEqualTo(0);
            assertThat(registry.get("jvm.gc.memory.allocated").counter().count()).isGreaterThanOrEqualTo(0);
            assertThat(registry.get("jvm.gc.pause").timer().count()).isGreaterThanOrEqualTo(0);

            // we can't test "jvm.gc.concurrent.phase.time" because its only exisiting after
            // a concurrent phase
        }
    }
}
