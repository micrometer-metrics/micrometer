package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CounterBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CounterBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(10)
                .mode(Mode.SampleTime)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    private int x = 923;
    private int y = 123;

    private MeterRegistry registry;
    private Counter counter;

    @Setup
    public void setup() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        counter = registry.counter("counter");
    }

    @Benchmark
    public int countSum() {
        counter.increment();
        return sum();
    }

    @Benchmark
    public int countSumWithRegistryLookup() {
        registry.counter("counter").increment();
        return sum();
    }

    @Benchmark
    public int sum() {
        return x + y;
    }
}
