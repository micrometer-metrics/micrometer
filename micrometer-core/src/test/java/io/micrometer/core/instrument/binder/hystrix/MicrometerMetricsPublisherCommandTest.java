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
package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricsPublisherCommandTest {

    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("MicrometerGROUP");

    private HystrixCommandProperties.Setter propertiesSetter;

    private MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void init() {
        Hystrix.reset();
        propertiesSetter = HystrixCommandProperties.Setter()
            .withCircuitBreakerEnabled(true)
            .withCircuitBreakerRequestVolumeThreshold(20)
            .withCircuitBreakerSleepWindowInMilliseconds(10_000)
            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
            .withExecutionTimeoutInMilliseconds(100);
    }

    @AfterEach
    void teardown() {
        Hystrix.reset();
    }

    @Test
    void cumulativeCounters() throws Exception {
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();
        HystrixPlugins.reset();
        HystrixPlugins.getInstance()
            .registerMetricsPublisher(new MicrometerMetricsPublisher(registry, metricsPublisher));
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-A");

        for (int i = 0; i < 3; i++) {
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new TimeoutCommand(key).execute();
            new SuccessCommand(key).execute();
            new FailureCommand(key).execute();
            new FailureCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new SuccessCommand(key).execute();
        }

        Iterable<Tag> tags = Tags.of("group", "MicrometerGROUP", "key", "MicrometerCOMMAND-A");

        assertExecutionMetric(tags, HystrixEventType.SUCCESS, 24.0);
        assertExecutionMetric(tags, HystrixEventType.TIMEOUT, 3.0);
        assertExecutionMetric(tags, HystrixEventType.FAILURE, 6.0);
        assertExecutionMetric(tags, HystrixEventType.SHORT_CIRCUITED, 0.0);
        assertThat(registry.get("hystrix.circuit.breaker.open").tags(tags).gauge().value()).isEqualTo(0.0);
    }

    private void assertExecutionMetric(Iterable<Tag> tags, HystrixEventType eventType, double count) {
        Iterable<Tag> myTags = Tags.concat(tags, "event", eventType.name().toLowerCase(Locale.ROOT), "terminal",
                Boolean.toString(eventType.isTerminal()));
        assertThat(registry.get("hystrix.execution").tags(myTags).counter().count()).isEqualTo(count);
    }

    @Test
    void openCircuit() {
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();
        HystrixPlugins.reset();
        HystrixPlugins.getInstance()
            .registerMetricsPublisher(new MicrometerMetricsPublisher(registry, metricsPublisher));
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-B");

        propertiesSetter.withCircuitBreakerForceOpen(true);

        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new TimeoutCommand(key).execute();
        new FailureCommand(key).execute();
        new FailureCommand(key).execute();
        new SuccessCommand(key).execute();

        Iterable<Tag> tags = Tags.of("group", groupKey.name(), "key", key.name());

        assertExecutionMetric(tags, HystrixEventType.SHORT_CIRCUITED, 6.0);
        assertExecutionMetric(tags, HystrixEventType.SUCCESS, 0.0);
        assertExecutionMetric(tags, HystrixEventType.TIMEOUT, 0.0);
        assertExecutionMetric(tags, HystrixEventType.FAILURE, 0.0);
        assertThat(registry.get("hystrix.circuit.breaker.open").tags(tags).gauge().value()).isEqualTo(1.0);
    }

    class SampleCommand extends HystrixCommand<Integer> {

        boolean shouldFail;

        int latencyToAdd;

        SampleCommand(HystrixCommandKey key, boolean shouldFail, int latencyToAdd) {
            super(Setter.withGroupKey(groupKey).andCommandKey(key).andCommandPropertiesDefaults(propertiesSetter));
            this.shouldFail = shouldFail;
            this.latencyToAdd = latencyToAdd;
        }

        @Override
        protected Integer run() throws Exception {
            if (shouldFail) {
                throw new RuntimeException("command failure");
            }
            else {
                Thread.sleep(latencyToAdd);
                return 1;
            }
        }

        @Override
        protected Integer getFallback() {
            return 99;
        }

    }

    class SuccessCommand extends SampleCommand {

        SuccessCommand(HystrixCommandKey key) {
            super(key, false, 0);
        }

        public SuccessCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, false, latencyToAdd);
        }

    }

    class FailureCommand extends SampleCommand {

        FailureCommand(HystrixCommandKey key) {
            super(key, true, 0);
        }

        public FailureCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, true, latencyToAdd);
        }

    }

    class TimeoutCommand extends SampleCommand {

        TimeoutCommand(HystrixCommandKey key) {
            super(key, false, 400); // exceeds 100ms timeout
        }

    }

}
