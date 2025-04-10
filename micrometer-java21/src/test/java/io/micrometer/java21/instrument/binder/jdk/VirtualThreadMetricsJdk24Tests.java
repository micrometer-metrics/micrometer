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
package io.micrometer.java21.instrument.binder.jdk;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("jdk24")
class VirtualThreadMetricsJdk24Tests {

    MeterRegistry registry = new SimpleMeterRegistry();

    VirtualThreadMetrics virtualThreadMetrics = new VirtualThreadMetrics();

    @BeforeEach
    void setUp() {
        virtualThreadMetrics.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        virtualThreadMetrics.close();
    }

    @Test
    void parallelism() {
        int expectedParallelism = Runtime.getRuntime().availableProcessors();
        assertThat(registry.get("jvm.threads.virtual.parallelism").gauge().value()).isEqualTo(expectedParallelism);
    }

    @Test
    void poolSize() throws InterruptedException {
        assertThat(registry.get("jvm.threads.virtual.pool.size").gauge().value()).isGreaterThanOrEqualTo(0);
        Thread.ofVirtual()
            .start(() -> assertThat(registry.get("jvm.threads.virtual.pool.size").gauge().value())
                .isGreaterThanOrEqualTo(1))
            .join(Duration.ofMillis(100));
    }

    @Test
    void mountedThreads() throws InterruptedException {
        Thread.ofVirtual()
            .start(() -> assertThat(
                    registry.get("jvm.threads.virtual.live").tag("scheduling.status", "mounted").gauge().value())
                .isEqualTo(1d))
            .join(Duration.ofMillis(100));
    }

    @Test
    void queuedThreads() throws InterruptedException {
        AtomicBoolean spin = new AtomicBoolean(true);
        Runnable spinWait = () -> {
            while (spin.get()) {
                Thread.onSpinWait();
            }
        };
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            try {
                for (int i = 0; i < parallelism; i++) {
                    executorService.submit(spinWait);
                }
                int expectedQueuedThreads = 7;
                for (int i = 0; i < expectedQueuedThreads; i++) {
                    executorService.submit(() -> {
                    });
                }
                assertThat(registry.get("jvm.threads.virtual.live").tag("scheduling.status", "queued").gauge().value())
                    .isGreaterThanOrEqualTo(expectedQueuedThreads);
            }
            finally {
                spin.set(false);
                executorService.shutdown();
                executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
            }
        }
    }

}
