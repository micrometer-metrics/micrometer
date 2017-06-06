package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface CustomCollectorChild {
    Stream<Collector.MetricFamilySamples.Sample> collect();

    default Iterable<Measurement> measure() {
        return collect().map(sample -> {
            Tag[] tags = IntStream.range(0, sample.labelNames.size())
                    .mapToObj(i -> Tag.of(sample.labelNames.get(i), sample.labelValues.get(i)))
                    .toArray(Tag[]::new);
            return new Measurement(sample.name, tags, sample.value);
        }).collect(Collectors.toList());
    }
}
