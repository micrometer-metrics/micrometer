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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.ListAssert;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentedQueuedThreadPoolTest {

    @Test
    void registeredMetricsShouldBeRemovedAfterClosingTheBinder() throws Exception {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        QueuedThreadPool instance = new InstrumentedQueuedThreadPool(meterRegistry, Tags.of("pool", "1"));

        instance.start();

        assertThatMetricsExist(meterRegistry);

        Gauge jobsGauge = meterRegistry.find("jetty.threads.jobs").gauge();

        assertThat(jobsGauge.getId().getTags()).as("ensure metrics have thread pool tag").contains(Tag.of("pool", "1"));

        instance.stop();

        assertThatMetricsDoNotExist(meterRegistry);
    }

    @Test
    void shouldOnlyRemoveMetricsBelongingToItsOwnPool() throws Exception {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        QueuedThreadPool pool1 = new InstrumentedQueuedThreadPool(meterRegistry, Tags.of("pool", "1"));

        QueuedThreadPool pool2 = new InstrumentedQueuedThreadPool(meterRegistry, Tags.of("pool", "2"));

        pool1.start();
        pool2.start();

        assertThatMetricsExist(meterRegistry);

        assertThat(meterRegistry.find("jetty.threads.jobs").tag("pool", "1").gauge()).as("pool 1 gauge exists")
            .isNotNull();
        assertThat(meterRegistry.find("jetty.threads.jobs").tag("pool", "2").gauge()).as("pool 2 gauge exists")
            .isNotNull();

        pool1.stop();

        assertThat(meterRegistry.find("jetty.threads.jobs").tag("pool", "1").gauge())
            .as("pool 1 gauge no longer exists")
            .isNull();
        assertThat(meterRegistry.find("jetty.threads.jobs").tag("pool", "2").gauge()).as("pool 2 gauge exists")
            .isNotNull();

        pool2.stop();

        assertThatMetricsDoNotExist(meterRegistry);
    }

    private void assertThatMetricsExist(MeterRegistry meterRegistry) {
        assertThatMetrics(meterRegistry, (l, a) -> a.containsAll(l));
    }

    private void assertThatMetricsDoNotExist(MeterRegistry meterRegistry) {
        assertThatMetrics(meterRegistry, (l, a) -> a.doesNotContainAnyElementsOf(l));
    }

    private void assertThatMetrics(MeterRegistry meterRegistry,
            BiFunction<Iterable<String>, ListAssert<String>, ListAssert<String>> assertFunction) {
        assertFunction.apply(
                Arrays.asList("jetty.threads.jobs", "jetty.threads.busy", "jetty.threads.idle",
                        "jetty.threads.config.max", "jetty.threads.config.min", "jetty.threads.current"),
                assertThat(
                        meterRegistry.getMeters().stream().map(m -> m.getId().getName()).collect(Collectors.toList())));
    }

}
