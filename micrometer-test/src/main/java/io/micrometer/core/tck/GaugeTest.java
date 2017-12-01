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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

interface GaugeTest {
    @Test
    @DisplayName("gauges attached to a number are updated when their values are observed")
    default void numericGauge(MeterRegistry registry) {
        AtomicInteger n = registry.gauge("my.gauge", new AtomicInteger(0));
        n.set(1);

        Gauge g = registry.mustFind("my.gauge").gauge();
        assertThat(g.value()).isEqualTo(1);

        n.set(2);
        assertThat(g.value()).isEqualTo(2);
    }

    @Test
    @DisplayName("gauges attached to an object are updated when their values are observed")
    default void objectGauge(MeterRegistry registry) {
        List<String> list = registry.gauge("my.gauge", emptyList(), new ArrayList<>(), List::size);
        list.addAll(Arrays.asList("a", "b"));

        Gauge g = registry.mustFind("my.gauge").gauge();
        assertThat(g.value()).isEqualTo(2);
    }

    @Test
    @DisplayName("gauges can be directly associated with collection size")
    default void collectionSizeGauge(MeterRegistry registry) {
        List<String> list = registry.gaugeCollectionSize("my.gauge", emptyList(), new ArrayList<>());
        list.addAll(Arrays.asList("a", "b"));

        Gauge g = registry.mustFind("my.gauge").gauge();
        assertThat(g.value()).isEqualTo(2);
    }

    @Test
    @DisplayName("gauges can be directly associated with map entry size")
    default void mapSizeGauge(MeterRegistry registry) {
        Map<String, Integer> map = registry.gaugeMapSize("my.gauge", emptyList(), new HashMap<>());
        map.put("a", 1);

        Gauge g = registry.mustFind("my.gauge").gauge();
        assertThat(g.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("gauges that reference an object that is garbage collected report NaN")
    default void garbageCollectedSourceObject(MeterRegistry registry) {
        registry.gauge("my.gauge", emptyList(), (Map) null, Map::size);
        assertThat(registry.mustFind("my.gauge").gauge().value()).matches(val -> Double.isNaN(val) || val == 0.0);
    }
}
