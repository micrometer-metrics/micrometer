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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentSampleTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void nestedSamples_parentChildThreadsInstrumented() throws ExecutionException, InterruptedException {
        ExecutorService taskRunner = ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(1), "myTaskRunner");

        Timer.Sample sample = Timer.start(registry);
        System.out.println("Outside task: " + sample);
        taskRunner.submit(() -> {
            System.out.println("In task: " + registry.getCurrentSample());
            assertThat(registry.getCurrentSample()).isNotEqualTo(sample);
        }).get();

        sample.stop(Timer.builder("my.service"));

        assertThat(registry.getCurrentSample()).isNull();
    }

    @Test
    void start_thenStopOnChildThread() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Timer.Sample sample = Timer.start(registry);

        executor.submit(() -> {
            sample.restore();
            assertThat(registry.getCurrentSample()).isEqualTo(sample);
            sample.stop(Timer.builder("my.timer"));
        }).get();

        assertThat(registry.getCurrentSample()).isNull();
    }

    @Test
    void startOnChildThread_thenStopOnSiblingThread() throws InterruptedException, ExecutionException {
        // thread pool with 2 threads, so a different thread is used for the 2 tasks
        // not guaranteed behavior
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, Timer.Sample> sampleMap = new HashMap<>();

        executor.submit(() -> {
            Timer.Sample sample = Timer.start(registry);
            sampleMap.put("mySample", sample);
            // once this task finishes, its ThreadLocal still has this sample as current
            // without this added code
            registry.removeCurrentSample(sample);
        }).get();

        executor.submit(() -> {
            Timer.Sample mySample = sampleMap.get("mySample");
            mySample.restore();
            assertThat(registry.getCurrentSample()).isEqualTo(mySample);
            mySample.stop(Timer.builder("my.timer"));
            assertThat(registry.getCurrentSample()).isNull();
        }).get();

        assertThat(registry.getCurrentSample()).isNull();
    }

    @Test
    void nestedSamples_sameThread() {
        Timer.Sample sample = Timer.start(registry);
        Timer.Sample sample2 = Timer.start(registry);

        sample.stop(Timer.builder("test1"));
        sample2.stop(Timer.builder("test2"));

        assertThat(registry.getCurrentSample()).isNull();
    }
}
