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
package io.micrometer.core.instrument;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.instrument.Statistic.Count;
import static io.micrometer.core.instrument.Statistic.Total;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A suite of tests applicable to all MeterRegistry implementations
 * @author Jon Schneider
 */
class MeterRegistryTest {

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
    @DisplayName("find meters by name and class type matching a subset of their tags")
    void findMeters(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c1);
        assertThat(registry.find("bar").tags("k", "v").counter()).containsSame(c2);
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("find meters by name and type matching a subset of their tags")
    void findMetersByType(MeterRegistry registry) {
        Counter c1 = registry.counter("foo", "k", "v");
        Counter c2 = registry.counter("bar", "k", "v", "k2", "v");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c1);
        assertThat(registry.find("bar").tags("k", "v").counter()).containsSame(c2);
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("find meters by name and value")
    void findMetersByValue(MeterRegistry registry) {
        Counter c = registry.counter("counter");
        c.increment();

        Timer t = registry.timer("timer");
        t.record(10, TimeUnit.NANOSECONDS);

        assertThat(registry.find("counter").value(Count, 1.0).counter()).isPresent();

        assertThat(registry.find("timer").value(Count, 1.0).timer()).isPresent();
        assertThat(registry.find("timer").value(Total, 10.0).timer()).isPresent();
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("common tags are added to every measurement")
    void addCommonTags(MeterRegistry registry) {
        registry.config().commonTags("k", "v");
        Counter c = registry.counter("foo");

        assertThat(registry.find("foo").tags("k", "v").counter()).containsSame(c);
    }

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("original and convention names are preserved for custom meter types")
    void aTaleOfTwoNames(MeterRegistry registry) {
        AtomicInteger n = new AtomicInteger(1);
        registry.more().counter(registry.createId("my.counter", Collections.emptyList(), null), n);

        assertThat(registry.find("my.counter").meter()).isPresent();
    }
}
