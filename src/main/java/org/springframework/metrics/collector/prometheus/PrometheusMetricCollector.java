package org.springframework.metrics.collector.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.PushGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.metrics.collector.*;

import java.io.IOException;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PrometheusMetricCollector extends AbstractMetricCollector {
    private CollectorRegistry registry;

    public PrometheusMetricCollector() {
        this(new CollectorRegistry(true));
    }

    @Autowired
    public PrometheusMetricCollector(CollectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Clock getClock() {
        // maps to Gauge.TimeProvider and SimpleTimer.TimeProvider in prometheus
        // FIXME inject me somehow
        return System::nanoTime;
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return register(new PrometheusCounter(withNameAndTags(io.prometheus.client.Gauge.build(), name, tags)));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return register(new PrometheusTimer(withNameAndTags(Summary.build(), name, tags), getClock()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        register(new PrometheusGauge(withNameAndTags(PrometheusToDoubleGuage.build(obj, f), name, tags)));
        return obj;
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
