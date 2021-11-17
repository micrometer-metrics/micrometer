/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentSampleTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void nestedThreadsCanGetSample() throws ExecutionException, InterruptedException {
        ExecutorService taskRunner = ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(1), "myTaskRunner");

        Timer.Sample sample = Timer.start(registry);
        System.out.println("Outside task: " + sample);
        Future<?> submittedTask = taskRunner.submit(() -> {
            System.out.println("In task: " + registry.getCurrentSample());
        });
        submittedTask.get();
        sample.stop(Timer.builder("my.service"));
    }

    @Test
    void startedAndStoppedOnDifferentThread() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Timer.Sample sample = Timer.start(registry);
        Future<?> task = executor.submit(() -> {
            assertThat(registry.getCurrentSample()).isEqualTo(sample);
            sample.stop(Timer.builder("my.timer"));
        });
        task.get();
        // fails because current sample removed in child thread not reflected in parent
        // with InheritableThreadLocal
        assertThat(registry.getCurrentSample()).isNull();
    }
}
