package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
public class TimerBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TimerBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(10)
                .mode(Mode.SampleTime)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    private MeterRegistry registry;
    private Timer timer;

    int x = 923;
    int y = 123;

    @Setup
    public void setup() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        timer = registry.timer("timer");
    }

    @Benchmark
    public int sumTimedWithSupplier() {
        return timer.record(this::sum);
    }

    @Benchmark
    public int sumTimedWithSample() {
        Timer.Sample sample = Timer.start(registry);
        int sum = sum();
        sample.stop(timer);
        return sum;
    }

    @Benchmark
    public int sumTimedWithRegistryLookup() {
        return registry.timer("timer").record(this::sum);
    }

    @Benchmark
    public int sum() {
        return x + y;
    }
}
