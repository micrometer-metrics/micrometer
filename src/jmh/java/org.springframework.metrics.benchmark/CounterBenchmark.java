package org.springframework.metrics.benchmark;

import com.netflix.spectator.api.Counter;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CounterBenchmark extends AbstractCollectorBenchmark {

    private Counter spectatorCounter;
    private com.codahale.metrics.Counter dropwizardCounter;

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
