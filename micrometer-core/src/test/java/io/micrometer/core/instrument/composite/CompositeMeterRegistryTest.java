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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class CompositeMeterRegistryTest {
    private CompositeMeterRegistry composite = new CompositeMeterRegistry();

    @Test
    void metricsAreInitiallyNoop() {
        // doesn't blow up
        composite.counter("counter").increment();
    }

    @DisplayName("metrics stop receiving updates when their registry parent is removed from a composite")
    void metricAfterRegistryRemove() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        composite.add(simple);

        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        Optional<Counter> simpleCounter = simple.find("counter").counter();
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));

        composite.remove(simple);
        compositeCounter.increment();

        // simple counter doesn't receive the increment after simple is removed from the composite
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));

        composite.add(simple);
        compositeCounter.increment();

        // now it receives updates again
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(2));
    }

    @DisplayName("metrics that are created before a registry is added are later added to that registry")
    @Test
    void metricBeforeRegistryAdd() {
        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        // increments are being NOOPd until there is a registry in the composite
        assertThat(compositeCounter.count()).isEqualTo(0);

        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        composite.add(simple);

        compositeCounter.increment();

        assertThat(compositeCounter.count()).isEqualTo(1);

        // only the increment AFTER simple is added to the composite is counted to it
        assertThat(simple.find("counter").counter()).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));
    }

    @DisplayName("metrics that are created after a registry is added to that registry")
    @Test
    void registryBeforeMetricAdd() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        composite.add(simple);
        composite.counter("counter").increment();

        assertThat(simple.find("counter").counter()).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));
    }
}
