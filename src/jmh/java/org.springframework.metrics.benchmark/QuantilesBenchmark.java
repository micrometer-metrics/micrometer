package org.springframework.metrics.benchmark;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.metrics.instrument.stats.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class QuantilesBenchmark {
    private RandomEngine r = new MersenneTwister64(0);
    private Normal dist = new Normal(100, 50, r);

    private Quantiles ckms;
    private Quantiles frugal2u;
    private Quantiles gk;
    private Quantiles window;

    @Setup
    public void setup() {
        ckms = CKMSQuantiles.build()
                .quantile(0.5, 0.05)
                .quantile(0.99, 0.01)
                .create();

        frugal2u = Frugal2UQuantiles.build()
                .quantile( 0.99, 100)
                .quantile( 0.99, 100)
                .create();

        gk = GKQuantiles.build()
                .quantile(0.5)
                .quantile(0.99)
                .create();

        window = WindowSketchQuantiles.build()
                .quantile(0.5)
                .quantile(0.99)
                .create();
    }

    @Benchmark
    public void frugal2uQuantiles() {
        frugal2u.observe(Math.max(0.0, dist.nextDouble()));
    }

    @Benchmark
    public void ckmsQuantiles() {
        ckms.observe(Math.max(0.0, dist.nextDouble()));
    }

    @Benchmark
    public void gkQuantiles() {
        gk.observe(Math.max(0.0, dist.nextDouble()));
    }

    @Benchmark
    public void windowQuantiles() {
        window.observe(Math.max(0.0, dist.nextDouble()));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(QuantilesBenchmark.class.getSimpleName())
                .warmupIterations(20)
                .measurementIterations(30)
                .mode(Mode.SampleTime)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
