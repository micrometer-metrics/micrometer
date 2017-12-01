/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCommandDefault;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricsPublisherCommandTest {
    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("MicrometerGROUP");
    private static HystrixCommandProperties.Setter propertiesSetter = HystrixCommandProperties.Setter()
        .withCircuitBreakerEnabled(true)
        .withCircuitBreakerRequestVolumeThreshold(20)
        .withCircuitBreakerSleepWindowInMilliseconds(10_000)
        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
        .withExecutionTimeoutInMilliseconds(100);

    @Disabled("CI is failing often asserting that the count is 23")
    @Test
    void testCumulativeCounters() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-A");
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key, groupKey, properties, metrics);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        MicrometerMetricsPublisherCommand metricsPublisherCommand = new MicrometerMetricsPublisherCommand(registry, key, groupKey, metrics, circuitBreaker, properties);
        metricsPublisherCommand.initialize();

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

        List<Tag> tags = Tags.zip("group", "MicrometerGROUP", "key", "MicrometerCOMMAND-A");

        assertExecutionMetric(registry, "success", 24.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "timeout").functionCounter().count()).isEqualTo(3.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "failure").functionCounter().count()).isEqualTo(6.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "short_circuited").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.mustFind("hystrix.circuit.breaker.open").tags(tags).gauge().value()).isEqualTo(0.0);
    }

    private void assertExecutionMetric(SimpleMeterRegistry registry, String eventType, double count) {
        try {
            Flux.interval(Duration.ofMillis(50))
                .takeUntil(n -> registry.mustFind("hystrix.execution").tags("event", eventType)
                    .functionCounter()
                    .count() == count)
                .blockLast(Duration.ofSeconds(30));
        } catch(RuntimeException e) {
            assertThat(registry.mustFind("hystrix.execution").tags("event", eventType)
                .functionCounter()
                .count()).isEqualTo(count);
        }
    }

    @Disabled
    @Test
    void testOpenCircuit() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-B");
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter.withCircuitBreakerForceOpen(true));
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key, groupKey, properties, metrics);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMetricsPublisherCommand metricsPublisherCommand = new MicrometerMetricsPublisherCommand(registry, key, groupKey, metrics, circuitBreaker, properties);
        metricsPublisherCommand.initialize();

        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new TimeoutCommand(key).execute();
        new FailureCommand(key).execute();
        new FailureCommand(key).execute();
        new SuccessCommand(key).execute();

        List<Tag> tags = Tags.zip("group", groupKey.name(), "key", key.name());

        assertExecutionMetric(registry, "short_circuited", 6.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "success").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "timeout").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.mustFind("hystrix.execution").tags(tags).tags("event", "failure").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.mustFind("hystrix.fallback").tags(tags).tags("event", "fallback_success").functionCounter().count()).isEqualTo(6.0);
        assertThat(registry.mustFind("hystrix.circuit.breaker.open").tags(tags).gauge().value()).isEqualTo(1.0);
    }

    static class SampleCommand extends HystrixCommand<Integer> {
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
            } else {
                Thread.sleep(latencyToAdd);
                return 1;
            }
        }

        @Override
        protected Integer getFallback() {
            return 99;
        }
    }

    static class SuccessCommand extends SampleCommand {
        SuccessCommand(HystrixCommandKey key) {
            super(key, false, 0);
        }

        public SuccessCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, false, latencyToAdd);
        }
    }

    static class FailureCommand extends SampleCommand {
        FailureCommand(HystrixCommandKey key) {
            super(key, true, 0);
        }

        public FailureCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, true, latencyToAdd);
        }
    }

    static class TimeoutCommand extends SampleCommand {
        TimeoutCommand(HystrixCommandKey key) {
            super(key, false, 400); //exceeds 100ms timeout
        }
    }
}
