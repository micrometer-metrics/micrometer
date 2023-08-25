/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.observation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentObservationTest {

    private final ObservationRegistry registry = ObservationRegistry.create();

    @BeforeEach
    void setup() {
        registry.observationConfig().observationHandler(context -> true);
    }

    @Test
    void nestedSamples_parentChildThreadsInstrumented() throws ExecutionException, InterruptedException {
        ExecutorService taskRunner = Executors.newSingleThreadExecutor();

        Observation observation = Observation.createNotStarted("test.observation", registry);
        System.out.println("Outside task: " + observation);
        assertThat(registry.getCurrentObservation()).isNull();
        try (Observation.Scope scope = observation.openScope()) {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            taskRunner.submit(() -> {
                System.out.println("In task: " + registry.getCurrentObservation());
                assertThat(registry.getCurrentObservation()).isNotEqualTo(observation);
            }).get();
        }
        assertThat(registry.getCurrentObservation()).isNull();
        observation.stop();
    }

    @Test
    void start_thenStopOnChildThread() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Observation observation = Observation.createNotStarted("test.observation", registry);
        assertThat(registry.getCurrentObservation()).isNull();
        executor.submit(() -> {
            try (Observation.Scope scope = observation.openScope()) {
                assertThat(registry.getCurrentObservation()).isSameAs(observation);
            }
            observation.stop();
        }).get();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void startOnChildThread_thenStopOnSiblingThread() throws InterruptedException, ExecutionException {
        // 2 thread pools with 1 thread each, so a different thread is used for the 2
        // tasks
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        Map<String, Observation> observationMap = new HashMap<>();

        executor.submit(() -> {
            Observation observation = Observation.createNotStarted("test.observation", registry);
            assertThat(registry.getCurrentObservation()).isNull();
            observationMap.put("myObservation", observation);
        }).get();

        executor2.submit(() -> {
            Observation myObservation = observationMap.get("myObservation");
            try (Observation.Scope scope = myObservation.openScope()) {
                assertThat(registry.getCurrentObservation()).isSameAs(myObservation);
            }
            myObservation.stop();
            assertThat(registry.getCurrentObservation()).isNull();
        }).get();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void nestedSamples_sameThread() {
        Observation observation = Observation.createNotStarted("observation1", registry);
        Observation observation2;
        assertThat(registry.getCurrentObservation()).isNull();
        try (Observation.Scope scope = observation.openScope()) {
            observation2 = Observation.createNotStarted("observation2", registry);
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
        }
        try (Observation.Scope scope = observation2.openScope()) {
            observation.stop();
            assertThat(registry.getCurrentObservation()).isSameAs(observation2);
        }
        observation2.stop();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void nestedScopes_makeCurrent() {
        Observation observation = Observation.createNotStarted("test.observation", registry);
        assertThat(registry.getCurrentObservationScope()).isNull();
        try (Observation.Scope scopeA = observation.openScope()) {
            assertThat(registry.getCurrentObservationScope()).isSameAs(scopeA);
            try (Observation.Scope scopeB = observation.openScope()) {
                assertThat(registry.getCurrentObservationScope()).isSameAs(scopeB);

                scopeA.makeCurrent();
                assertThat(registry.getCurrentObservationScope()).isSameAs(scopeA);
            }
            assertThat(registry.getCurrentObservationScope()).isSameAs(scopeA);
        }
        assertThat(registry.getCurrentObservationScope()).isNull();
    }

}
