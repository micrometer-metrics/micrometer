/*
 * Copyright 2026 VMware, Inc.
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExecutorServiceMetrics}.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
class ExecutorServiceMetricsTests {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void monitorWithExecutorsNewVirtualThreadPerTaskExecutorWillBeDisabledWhenReflectionIsNotEnabled() {
        ExecutorService unmonitored = Executors.newVirtualThreadPerTaskExecutor();
        assertThat(unmonitored.getClass().getName()).isEqualTo("java.util.concurrent.ThreadPerTaskExecutor");
        ExecutorService monitored = ExecutorServiceMetrics.monitor(registry, unmonitored, "test");
        monitored.execute(() -> {
        });
        assertThat(registry.find("executor.active").gauge()).isNull();
    }

}
