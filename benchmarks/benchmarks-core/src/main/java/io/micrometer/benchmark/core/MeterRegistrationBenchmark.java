package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MeterRegistrationBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MeterRegistrationBenchmark.class.getSimpleName())
                .warmupIterations(2)
                .measurementIterations(5)
                .mode(Mode.SampleTime)
                .timeUnit(TimeUnit.SECONDS)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    private int x = 923;
    private int y = 123;

    @Benchmark
    public int insert10_000() {
        MeterRegistry registry = new SimpleMeterRegistry();
        for (int i = 0; i < 10_000; i++) {
            registry.counter("my.counter", "k" + i, "v1");
        }
        return sum();
    }

    @Benchmark
    public int sum() {
        return x + y;
    }
}
