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
package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.stats.quantile.GKQuantiles;

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
        SpectatorMeterRegistry registry = new SpectatorMeterRegistry();
        Registry spectatorRegistry = registry.getSpectatorRegistry();

        registry.timerBuilder("timer")
                .quantiles(GKQuantiles.quantiles(0.5).create())
                .create();

        registry.summaryBuilder("ds")
                .quantiles(GKQuantiles.quantiles(0.5).create())
                .create();

        assertThat(spectatorRegistry).haveAtLeastOne(withNameAndTagKey("timer", "quantile"));
        assertThat(spectatorRegistry).haveAtLeastOne(withNameAndTagKey("ds", "quantile"));
    }

    private Condition<Meter> withNameAndTagKey(String name, String tagKey) {
        Predicate<Id> test = id -> id.name().equals(name) && stream(id.tags()).anyMatch(t -> t.key().equals(tagKey));
        return new Condition<>(m -> test.test(m.id()), "a meter with name `%s` and tag `%s`", name, tagKey);
    }
}
