/*
 * Copyright 2025 VMware, Inc.
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExecutorServiceMetrics} with reflection enabled.
 *
 * @author Tommy Ludwig
 */
@Tag("reflective")
class ExecutorServiceMetricsReflectiveTests {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void threadPoolMetricsWith_AutoShutdownDelegatedExecutorService() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService unmonitored = Executors.newSingleThreadExecutor();
        assertThat(unmonitored.getClass().getName())
            .isEqualTo("java.util.concurrent.Executors$AutoShutdownDelegatedExecutorService");
        ExecutorService monitored = ExecutorServiceMetrics.monitor(registry, unmonitored, "test");
        monitored.execute(latch::countDown);
        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(registry.get("executor.completed").tag("name", "test").functionCounter().count()).isEqualTo(1L);
    }

}
