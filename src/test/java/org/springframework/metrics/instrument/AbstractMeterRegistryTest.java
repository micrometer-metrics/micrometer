/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;

class AbstractMeterRegistryTest {

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("meters with the same name and tags are registered once")
    void uniqueMeters(MeterRegistry registry) {
        registry.counter("foo");
        registry.counter("foo");

        assertThat(registry.getMeters().size()).isEqualTo(1);
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("same meter name but subset of tags")
    void tagSubsets(MeterRegistry registry) {
        registry.counter("foo", "k", "v");

        AbstractThrowableAssert<?, ? extends Throwable> subsetAssert = assertThatCode(() ->
                registry.counter("foo", "k", "v", "k2", "v2"));

        if (registry instanceof PrometheusMeterRegistry) {
            // Prometheus requires a fixed set of tags per meter
            subsetAssert.hasMessage("Incorrect number of labels.");
        } else {
            subsetAssert.doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("find meters by name matching a subset of their tags")
    void findMeters(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.findMeter(Counter.class, "foo", "k", "v"))
                .containsSame(c1);

        assertThat(registry.findMeter(Counter.class, "bar", "k", "v"))
                .containsSame(c2);
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    void monitorExecutorService(MeterRegistry registry) throws InterruptedException {
        ExecutorService pool = registry.monitor("beep_pool", Executors.newSingleThreadExecutor());
        CountDownLatch taskStart = new CountDownLatch(1);
        CountDownLatch taskComplete = new CountDownLatch(1);

        pool.submit(() -> {
            taskStart.countDown();
            taskComplete.await();
            System.out.println("beep");
            return 0;
        });
        pool.submit(() -> System.out.println("boop"));

        taskStart.await();
        assertThat(registry.findMeter(Gauge.class, "beep_pool_queued"))
                .hasValueSatisfying(g -> assertThat(g.value()).isEqualTo(1, offset(1e-12)));

        taskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.findMeter(Timer.class, "beep_pool_duration"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(2));
        assertThat(registry.findMeter(Gauge.class, "beep_pool_queued"))
                .hasValueSatisfying(g -> assertThat(g.value()).isEqualTo(0, offset(1e-12)));
    }
}
