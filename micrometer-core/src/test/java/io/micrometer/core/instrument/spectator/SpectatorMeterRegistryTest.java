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
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.quantile.GKQuantiles;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.google.common.collect.Streams.stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class SpectatorMeterRegistryTest {

    @DisplayName("quantiles are registered as a separate gauge")
    @Test
    void quantiles() {
        SpectatorMeterRegistry registry = new SpectatorMeterRegistry(new DefaultRegistry(), Clock.SYSTEM) {};
        Registry spectatorRegistry = registry.getSpectatorRegistry();

        Timer timer = Timer.builder("timer")
                .quantiles(GKQuantiles.quantiles(0.5, 0.95).create())
                .register(registry);

        timer.record(100, TimeUnit.MILLISECONDS);

        DistributionSummary.builder("ds")
                .quantiles(GKQuantiles.quantiles(0.5).create())
                .register(registry);

        assertThat(spectatorRegistry).haveAtLeastOne(withNameAndQuantile("timer"));
        assertThat(spectatorRegistry).haveAtLeastOne(withNameAndQuantile("ds"));

        assertThat(spectatorRegistry).haveAtLeast(2,
                new Condition<>(m -> quantilePredicate("timer").test(m.id()) && m.measure().iterator().next().value() != Double.NaN,
                        "a meter with at least two quantiles where both quantiles have a value"));
    }

    private Condition<Meter> withNameAndQuantile(String name) {
        Predicate<Id> test = quantilePredicate(name);
        return new Condition<>(m -> test.test(m.id()), "a meter with name `%s` and tag `%s`", name, "quantile");
    }

    private Predicate<Id> quantilePredicate(String name) {
        return id -> id.name().equals(name) && stream(id.tags()).anyMatch(t -> t.key().equals("quantile"));
    }
}
