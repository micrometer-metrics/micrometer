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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricsPublisherThreadPoolTest {

    private static final String NAME_HYSTRIX_THREADPOOL = "hystrix.threadpool";

    private static final String NAME_MICROMETER_GROUP = "MicrometerGROUP";

    private MeterRegistry registry = new SimpleMeterRegistry();

    private HystrixCommandProperties.Setter propertiesSetter = HystrixCommandProperties.Setter()
        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);

    private final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(NAME_MICROMETER_GROUP);

    @BeforeEach
    void init() {
        Hystrix.reset();
    }

    @AfterEach
    void teardown() {
        Hystrix.reset();
    }

    /**
     * Test that thread pool metrics are published.
     */
    @Test
    void testMetricIds() {
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();
        HystrixPlugins.reset();
        HystrixPlugins.getInstance()
            .registerMetricsPublisher(new MicrometerMetricsPublisher(registry, metricsPublisher));

        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-A");
        new SampleCommand(key).execute();

        final Tags tags = Tags.of("key", NAME_MICROMETER_GROUP);

        final Set<MeterId> actualMeterIds = registry.getMeters()
            .stream()
            .map(meter -> new MeterId(meter.getId().getName(), meter.getId().getType(),
                    Tags.of(meter.getId().getTags())))
            .collect(Collectors.toSet());

        final Set<MeterId> expectedMeterIds = new HashSet<>(Arrays.asList(
                new MeterId(metricName("threads.active.current.count"), Type.GAUGE, tags),
                new MeterId(metricName("threads.cumulative.count"), Type.COUNTER,
                        tags.and(Tags.of("type", "executed"))),
                new MeterId(metricName("threads.cumulative.count"), Type.COUNTER,
                        tags.and(Tags.of("type", "rejected"))),
                new MeterId(metricName("threads.pool.current.size"), Type.GAUGE, tags),
                new MeterId(metricName("threads.largest.pool.current.size"), Type.GAUGE, tags),
                new MeterId(metricName("threads.max.pool.current.size"), Type.GAUGE, tags),
                new MeterId(metricName("threads.core.pool.current.size"), Type.GAUGE, tags),
                new MeterId(metricName("tasks.cumulative.count"), Type.COUNTER, tags.and(Tags.of("type", "scheduled"))),
                new MeterId(metricName("tasks.cumulative.count"), Type.COUNTER, tags.and(Tags.of("type", "completed"))),
                new MeterId(metricName("queue.current.size"), Type.GAUGE, tags),
                new MeterId(metricName("queue.max.size"), Type.GAUGE, tags),
                new MeterId(metricName("queue.rejection.threshold.size"), Type.GAUGE, tags)));

        assertThat(actualMeterIds).containsAll(expectedMeterIds);
    }

    private class SampleCommand extends HystrixCommand<Integer> {

        SampleCommand(HystrixCommandKey key) {
            super(Setter.withGroupKey(groupKey).andCommandKey(key).andCommandPropertiesDefaults(propertiesSetter));
        }

        @Override
        protected Integer run() {
            return 1;
        }

    }

    private static class MeterId {

        private final String name;

        private final Meter.Type type;

        private final Tags tags;

        MeterId(final String name, final Type type, final Tags tags) {
            this.name = name;
            this.type = type;
            this.tags = tags;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MeterId meterId = (MeterId) o;
            return Objects.equals(name, meterId.name) && type == meterId.type && Objects.equals(tags, meterId.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, tags);
        }

    }

    private static String metricName(String name) {
        return String.join(".", NAME_HYSTRIX_THREADPOOL, name);
    }

}
