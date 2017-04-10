package org.springframework.metrics.benchmark;

import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.DefaultCounterService;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CollectorBenchmark {

    private Registry spectatorRegistry = new DefaultRegistry();
    private Counter spectatorCounter;

    private CounterService bootCounterService = new DefaultCounterService(new InMemoryMetricRepository());

    private static io.prometheus.client.Counter prometheusCounter = io.prometheus.client.Counter
            .build("count", "Count nothing").register();

    private MetricRegistry dropwizardRegistry = new MetricRegistry();
    private com.codahale.metrics.Counter dropwizardCounter;

    private static final StatsDClient statsd = new NonBlockingStatsDClient(
            "prefix",
            "localhost",
            8125,
            "tag:value");

    @Setup
    public void setup() {
        spectatorCounter = spectatorRegistry.counter("count");
        dropwizardCounter = dropwizardRegistry.counter("count");
    }

    @Benchmark
    public void spectatorCounter() {
        spectatorCounter.increment();
    }

    @Benchmark
    public void bootCounter() {
        bootCounterService.increment("count");
    }

    @Benchmark
    public void prometheusCounter() {
        prometheusCounter.inc();
    }

    @Benchmark
    public void datadogStatsdCounter() {
        statsd.incrementCounter("count");
    }

    @Benchmark
    public void dropwizardCounter() {
        dropwizardCounter.inc();
    }
}
