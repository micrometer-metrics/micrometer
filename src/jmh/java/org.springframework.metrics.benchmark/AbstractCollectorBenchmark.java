package org.springframework.metrics.benchmark;

import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.DefaultCounterService;

class AbstractCollectorBenchmark {
    Registry spectatorRegistry = new DefaultRegistry();
    CounterService bootCounterService = new DefaultCounterService(new InMemoryMetricRepository());
    static io.prometheus.client.Counter prometheusCounter = io.prometheus.client.Counter
            .build("count", "Count nothing")
            .register();

    MetricRegistry dropwizardRegistry = new MetricRegistry();

    static final StatsDClient statsd = new NonBlockingStatsDClient(
            "prefix",
            "localhost",
            8125,
            "tag:value");
}
