/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.scheduling;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@Ignore("Race condition still...")
public class ScheduledMethodMetricsTest {

    static CountDownLatch longTaskStarted = new CountDownLatch(1);
    static CountDownLatch longTaskShouldComplete = new CountDownLatch(1);

    static CountDownLatch shortBeepsExecuted = new CountDownLatch(1);

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ThreadPoolTaskScheduler scheduler;

    @Test
    public void shortTasksAreInstrumented() throws InterruptedException {
        shortBeepsExecuted.await();
        while (scheduler.getActiveCount() > 0) {
        }

        assertThat(registry.get("beeper").timer().count()).isEqualTo(1L);
        registry.get("beeper").tags("percentile", "50").gauge();
        registry.get("beeper").tags("percentile", "95").gauge();
    }

    @Test
    public void longTasksAreInstrumented() throws InterruptedException {
        longTaskStarted.await();

        assertThat(registry.get("long.beep").longTaskTimer().activeTasks()).isEqualTo(1);

        // make sure longBeep continues running until we have a chance to observe it in the active state
        longTaskShouldComplete.countDown();
        while (scheduler.getActiveCount() > 0) {
        }

        Thread.sleep(10);

        assertThat(registry.get("long.beep").longTaskTimer().activeTasks()).isEqualTo(0);
    }

    @SpringBootApplication
    @EnableScheduling
    static class MetricsApp {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ThreadPoolTaskScheduler scheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            // this way, executing longBeep doesn't block the short tasks from running
            scheduler.setPoolSize(6);
            return scheduler;
        }

        @Timed(value = "long.beep", longTask = true)
        @Scheduled(fixedDelay = 100_000)
        void longBeep() throws InterruptedException {
            longTaskStarted.countDown();
            longTaskShouldComplete.await();
            System.out.println("beep");
        }

        @Timed(value = "beeper", percentiles = {0.50, 0.95})
        @Scheduled(fixedDelay = 100_000)
        void shortBeep() {
            shortBeepsExecuted.countDown();
            System.out.println("beep");
        }

        @Timed // not instrumented because @Timed lacks a metric name
        @Scheduled(fixedDelay = 100_000)
        void noMetricName() {
            System.out.println("beep");
        }

        @Scheduled(fixedDelay = 100_000)
            // not instrumented because it isn't @Timed
        void notTimed() {
            System.out.println("beep");
        }
    }
}
