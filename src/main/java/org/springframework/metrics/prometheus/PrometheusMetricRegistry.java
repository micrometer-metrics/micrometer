package org.springframework.metrics.prometheus;

import io.prometheus.client.*;
import io.prometheus.client.Gauge;
import org.springframework.metrics.*;
import org.springframework.metrics.Counter;

import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PrometheusMetricRegistry extends AbstractMetricRegistry {
    private CollectorRegistry registry;

    public PrometheusMetricRegistry() {
        this(new CollectorRegistry(true));
    }

    public PrometheusMetricRegistry(CollectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Clock getClock() {
        // FIXME inject me
        return System::nanoTime;
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return register(new PrometheusCounter(withNameAndTags(io.prometheus.client.Counter.build(), name, tags)));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return register(new PrometheusTimer(withNameAndTags(Summary.build(), name, tags), getClock()));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        register(new PrometheusGauge(withNameAndTags(Gauge.build(), name, tags)));
        return obj;
    }

    @Override
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return null;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> D withNameAndTags(
            SimpleCollector.Builder<B, C> builder, String name, Iterable<Tag> tags) {

        return builder
                .name(name)
                .help(" ")
                .labelNames(StreamSupport.stream(tags.spliterator(), false)
                        .map(Tag::getKey)
                        .collect(Collectors.toList())
                        .toArray(new String[]{}))
                .register(registry)
                .labels(StreamSupport.stream(tags.spliterator(), false)
                        .map(Tag::getValue)
                        .collect(Collectors.toList())
                        .toArray(new String[]{}));
    }
}
