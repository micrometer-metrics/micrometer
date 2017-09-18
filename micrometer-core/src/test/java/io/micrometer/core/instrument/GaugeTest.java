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
package io.micrometer.core.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GaugeTest {

    @DisplayName("gauges attached to a number are updated when their values are observed")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void numericGauge(MeterRegistry registry) {
        AtomicInteger n = registry.gauge("my.gauge", new AtomicInteger(0));
        n.set(1);

        Gauge g = singleGauge(registry);
        assertEquals(1, g.value(), 1.0e-12);

        n.set(2);
        assertEquals(2, g.value(), 1.0e-12);
    }

    @DisplayName("gauges attached to an object are updated when their values are observed")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void objectGauge(MeterRegistry registry) {
        List<String> list = registry.gauge("my.gauge", emptyList(), new ArrayList<>(), List::size);
        list.addAll(Arrays.asList("a", "b"));

        assertEquals(2, singleGauge(registry).value());
    }

    @DisplayName("gauges can be directly associated with collection size")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void collectionSizeGauge(MeterRegistry registry) {
        List<String> list = registry.gaugeCollectionSize("my.gauge", emptyList(), new ArrayList<>());
        list.addAll(Arrays.asList("a", "b"));

        assertEquals(2, singleGauge(registry).value());
    }

    @DisplayName("gauges can be directly associated with map entry size")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void mapSizeGauge(MeterRegistry registry) {
        Map<String, Integer> map = registry.gaugeMapSize("my.gauge", emptyList(), new HashMap<>());
        map.put("a", 1);

        assertEquals(1, singleGauge(registry).value());
    }
    
    @DisplayName("gauges that reference an object that is garbage collected report NaN")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void garbageCollectedSourceObject(MeterRegistry registry) {
        registry.gauge("my.gauge", emptyList(), (Map) null, Map::size);
        assertThat(registry.find("my.gauge").value(Statistic.Value, 0).gauge()).isPresent();
    }

    private Gauge singleGauge(MeterRegistry registry) {
        return (Gauge) registry.getMeters().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected a gauge to be registered"));
    }
}
