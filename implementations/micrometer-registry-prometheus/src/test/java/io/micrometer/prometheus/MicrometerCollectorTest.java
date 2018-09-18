package io.micrometer.prometheus;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.client.Collector;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static java.util.Collections.singletonList;

class MicrometerCollectorTest {
    @Issue("#769")
    @Test
    void manyTags() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id, NamingConvention.dot, PrometheusConfig.DEFAULT);

        for (Integer i = 0; i < 20_000; i++) {
            Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample("my_counter",
                    singletonList("k"), singletonList(i.toString()), 1.0);

            collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER,
                    "my_counter", sample)));
        }

        // Threw StackOverflowException because of too many nested streams originally
        collector.collect();
    }
}