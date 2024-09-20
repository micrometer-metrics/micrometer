/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link VirtualThreadMetrics}.
 *
 * @author Artyom Gabeev
 * @author Jonatan Ivanov
 */
class VirtualThreadMetricsTests {

    private static final Tags TAGS = Tags.of("k", "v");

    private SimpleMeterRegistry registry;

    private VirtualThreadMetrics virtualThreadMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        virtualThreadMetrics = new VirtualThreadMetrics(TAGS);
        virtualThreadMetrics.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        virtualThreadMetrics.close();
    }

    @Test
    void pinnedEventsShouldBeRecorded() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(executor.submit(() -> pinCurrentThreadAndAwait(latch)));
            }
            sleep(Duration.ofMillis(50)); // the time the threads will be pinned for
            latch.countDown();
            for (Future<?> future : futures) {
                waitFor(future);
            }

            Timer timer = registry.get("jvm.threads.virtual.pinned").tags(TAGS).timer();
            await().atMost(Duration.ofSeconds(2)).until(() -> timer.count() == 3);
            assertThat(timer.max(MILLISECONDS)).isBetween(40d, 60d); // ~50ms
            assertThat(timer.totalTime(MILLISECONDS)).isBetween(130d, 170d); // ~150ms
        }
    }

    private void pinCurrentThreadAndAwait(CountDownLatch latch) {
        synchronized (new Object()) { // assumes that synchronized pins the thread
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for latch");
                }
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitFor(Future<?> future) {
        try {
            future.get();
        }
        catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            future.cancel(true);
        }
    }

}
