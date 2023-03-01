/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.logging;

import io.micrometer.core.Issue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Made as a separate test class from LogbackMetricsTest since that class will cause LoggerFactory initialization separately from this test
@Issue("#2868")
class LogbackMetricsGh2868Test {

    @Test
    void concurrentInitializationClassNotFound() throws BrokenBarrierException, InterruptedException {
        int numberOfThreads = 10;
        final AtomicReference<Throwable> lastException = new AtomicReference<>();
        final CyclicBarrier syncGate = new CyclicBarrier(numberOfThreads + 1);
        final CyclicBarrier finishGate = new CyclicBarrier(numberOfThreads + 1);
        for (int i = 0; i < numberOfThreads; i++) {
            new Thread(() -> {
                try {
                    syncGate.await();
                    new LogbackMetrics();
                }
                catch (Exception e) {
                    lastException.set(e);
                    throw new RuntimeException(e);
                }
                finally {
                    try {
                        finishGate.await();
                    }
                    catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }
        syncGate.await();
        finishGate.await();
        assertThat(lastException.get()).isNull();
    }

}
