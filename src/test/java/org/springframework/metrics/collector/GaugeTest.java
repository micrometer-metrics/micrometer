package org.springframework.metrics.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GaugeTest {

    @DisplayName("gauges attached to a number are updated when their values are observed")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void numericGauge(MetricCollector collector) {
        AtomicInteger n = collector.gauge("myGauge", new AtomicInteger(0));
        n.set(1);

        Gauge g = singleGauge(collector);
        assertEquals(1, g.value(), 1.0e-12);

        n.set(2);
        assertEquals(2, g.value(), 1.0e-12);
    }

//    @DisplayName("gauges attached to an object are updated when their values are observed")
//    @ParameterizedTest
//    @ArgumentsSource(MetricCollectorsProvider.class)
//    void objectGauge(MetricCollector collector) {
//        List<String> list = collector.gauge("myGauge", Collections.emptyList(), new ArrayList<>(), List::size);
//        list.addAll(Arrays.asList("a", "b"));
//
//        assertEquals(2, singleGauge(collector).value());
//    }
//
//    @DisplayName("gauges can be directly associated with collection size")
//    @ParameterizedTest
//    @ArgumentsSource(MetricCollectorsProvider.class)
//    void collectionSizeGauge(MetricCollector collector) {
//        List<String> list = collector.collectionSize("myGauge", new ArrayList<>());
//        list.addAll(Arrays.asList("a", "b"));
//
//        assertEquals(2, singleGauge(collector).value());
//    }
//
//    @DisplayName("gauges can be directly associated with map entry size")
//    @ParameterizedTest
//    @ArgumentsSource(MetricCollectorsProvider.class)
//    void mapSizeGauge(MetricCollector collector) {
//        Map<String, Integer> map = collector.mapSize("myGauge", new HashMap<>());
//        map.put("a", 1);
//
//        assertEquals(1, singleGauge(collector).value());
//    }

    private Gauge singleGauge(MetricCollector collector) {
        return (Gauge) collector.getMeters().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected a gauge to be registered"));
    }
}
