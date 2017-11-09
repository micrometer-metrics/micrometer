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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.core.instrument.Statistic.Count;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class CompositeMeterRegistryTest {
    private CompositeMeterRegistry composite = new CompositeMeterRegistry();
    private SimpleMeterRegistry simple = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    @Test
    void metricsAreInitiallyNoop() {
        // doesn't blow up
        composite.counter("counter").increment();
    }

    @DisplayName("base units on meters that support them are passed through underlying registries")
    @Test
    void baseUnitsPreserved() {
        composite.add(simple);

        Counter.builder("counter").baseUnit("bytes").register(composite);
        DistributionSummary.builder("summary").baseUnit("bytes").register(composite);
        Gauge.builder("gauge", new AtomicInteger(0), AtomicInteger::get).baseUnit("bytes").register(composite);

        assertThat(simple.find("counter").counter())
            .hasValueSatisfying(c -> assertThat(c.getId().getBaseUnit()).isEqualTo("bytes"));
        assertThat(simple.find("summary").summary())
            .hasValueSatisfying(s -> assertThat(s.getId().getBaseUnit()).isEqualTo("bytes"));
        assertThat(simple.find("gauge").gauge())
            .hasValueSatisfying(g -> assertThat(g.getId().getBaseUnit()).isEqualTo("bytes"));
    }

    @DisplayName("metrics stop receiving updates when their registry parent is removed from a composite")
    @Test
    void metricAfterRegistryRemove() {
        composite.add(simple);

        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        Optional<Counter> simpleCounter = simple.find("counter").counter();
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));

        composite.remove(simple);
        compositeCounter.increment();

        // simple counter doesn't receive the increment after simple is removed from the composite
        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(0));

        composite.add(simple);
        compositeCounter.increment();

        // now it receives updates again
        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(simpleCounter).hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));
    }

    @DisplayName("metrics that are created before a registry is added are later added to that registry")
    @Test
    void metricBeforeRegistryAdd() {
        Counter compositeCounter = composite.counter("counter");
        compositeCounter.increment();

        // increments are being NOOPd until there is a registry in the composite
        assertThat(compositeCounter.count()).isEqualTo(0);

        composite.add(simple);

        compositeCounter.increment();

        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(compositeCounter.count()).isEqualTo(1);

        // only the increment AFTER simple is added to the composite is counted to it
        assertThat(simple.find("counter").value(Count, 1.0).counter()).isPresent();
    }

    @DisplayName("metrics that are created after a registry is added to that registry")
    @Test
    void registryBeforeMetricAdd() {
        composite.add(simple);
        composite.counter("counter").increment();

        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(simple.find("counter").value(Count, 1.0).counter()).isPresent();
    }

    @DisplayName("metrics follow the naming convention of each registry in the composite")
    @Test
    void namingConventions() {
        simple.config().namingConvention(NamingConvention.camelCase);

        composite.add(simple);
        composite.counter("my.counter").increment();

        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(simple.find("my.counter").value(Count, 1.0).counter()).isPresent();
    }

    @DisplayName("common tags added to the composite affect meters registered with registries in the composite")
    @Test
    void commonTags() {
        simple.config().commonTags("instance", "local"); // added alongside other common tags in the composite
        simple.config().commonTags("region", "us-west-1"); // overriden by the composite

        composite.config().commonTags("region", "us-east-1");
        composite.add(simple);
        composite.config().commonTags("stack", "test");

        composite.counter("counter").increment();

        assertThat(simple.find("counter").tags("region", "us-east-1", "stack", "test",
            "instance", "local").counter()).isPresent();
    }

    @DisplayName("function timer base units are delegated to registries in the composite")
    @Test
    void functionTimerUnits() {
        composite.add(simple);
        Object o = new Object();

        composite.more().timer("function.timer", emptyList(),
            o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        clock(simple).add(SimpleConfig.DEFAULT_STEP);
        assertThat(simple.find("function.timer").meter().map(Meter::measure))
            .hasValueSatisfying(measurements ->
                assertThat(measurements)
                    .anySatisfy(ms -> {
                        assertThat(ms.getStatistic()).isEqualTo(Statistic.TotalTime);
                        assertThat(ms.getValue()).isEqualTo(1e-3);
                    })
            );
    }
}
