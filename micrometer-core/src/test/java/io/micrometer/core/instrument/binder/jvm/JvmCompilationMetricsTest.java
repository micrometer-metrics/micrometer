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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link JvmCompilationMetrics}.
 */
class JvmCompilationMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void compilationTimeMetric() {
        new JvmCompilationMetrics().bindTo(registry);

        CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
        assumeTrue(compilationMXBean != null && compilationMXBean.isCompilationTimeMonitoringSupported());

        assertThat(registry.get("jvm.compilation.time").functionCounter().count()).isGreaterThan(0);
    }
}
