/*
 * Copyright 2018 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.benchmark.compare;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.UniformReservoir;
//CHECKSTYLE:OFF
import com.google.common.collect.Iterators;
import com.google.common.primitives.Doubles;
////CHECKSTYLE:ON
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.PercentileHistogramBuckets;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author John Karp
 */
@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(16)
public class CompareHistogramsWithOtherLibraries {

    @State(Scope.Thread)
    public static class Data {

        Iterator<Long> dataIterator;

        @Setup(Level.Iteration)
        public void setup() {
            final Random r = new Random(1234567891L);
            dataIterator = Iterators.cycle(Stream.generate(() -> Math.round(Math.exp(2.0 + r.nextGaussian())))
                .limit(1048576)
                .collect(Collectors.toList()));
        }

    }

    @State(Scope.Benchmark)
    public static class DropwizardState {

        MetricRegistry registry;

        Histogram histogram;

        Histogram histogramSlidingTimeWindow;

        Histogram histogramUniform;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new MetricRegistry();
            histogram = registry.histogram("histogram");
            histogramSlidingTimeWindow = registry.register("slidingTimeWindowHistogram",
                    new Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)));
            histogramUniform = registry.register("uniformHistogram", new Histogram(new UniformReservoir()));
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(histogram.getSnapshot().getMedian());
            hole.consume(histogramSlidingTimeWindow.getSnapshot().getMedian());
            hole.consume(histogramUniform.getSnapshot().getMedian());
        }

    }

    @State(Scope.Benchmark)
    public static class MicrometerState {

        io.micrometer.core.instrument.MeterRegistry registry;

        io.micrometer.core.instrument.DistributionSummary summary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), Clock.SYSTEM);
            summary = DistributionSummary.builder("summary").publishPercentileHistogram().register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(summary.takeSnapshot().count());
        }

    }

    @State(Scope.Benchmark)
    public static class MicrometerPlainSummaryState {

        io.micrometer.core.instrument.MeterRegistry registry;

        io.micrometer.core.instrument.DistributionSummary summary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new SimpleMeterRegistry();
            summary = registry.summary("summary");
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(summary.takeSnapshot().count());
        }

    }

    @State(Scope.Benchmark)
    public static class PrometheusState {

        io.prometheus.metrics.core.metrics.Histogram histogram;

        @Setup(Level.Trial)
        public void setup() {
            double[] micrometerBuckets = Doubles
                .toArray(PercentileHistogramBuckets.buckets(DistributionStatisticConfig.builder()
                    .minimumExpectedValue(0.0)
                    .maximumExpectedValue(Double.POSITIVE_INFINITY)
                    .percentilesHistogram(true)
                    .build()));
            histogram = io.prometheus.metrics.core.metrics.Histogram.builder()
                .name("histogram")
                .help("A histogram")
                .classicUpperBounds(micrometerBuckets)
                .register();
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(histogram.collect());
        }

    }

    @Benchmark
    public void micrometerPlainHistogram(MicrometerPlainSummaryState state, Data data) {
        state.summary.record(1);
    }

    // @Benchmark
    public void micrometerHistogram(MicrometerState state, Data data) {
        state.summary.record(data.dataIterator.next());
    }

    // @Benchmark
    public void dropwizardHistogram(DropwizardState state, Data data) {
        state.histogram.update(data.dataIterator.next());
    }

    // This benchmark is likely broken, results vary wildly between runs.
    // @Benchmark
    public void dropwizardHistogramSlidingTimeWindow(DropwizardState state, Data data) {
        state.histogramSlidingTimeWindow.update(data.dataIterator.next());
    }

    // @Benchmark
    public void dropwizardHistogramUniform(DropwizardState state, Data data) {
        state.histogramUniform.update(data.dataIterator.next());
    }

    // @Benchmark
    public void prometheusHistogram(PrometheusState state, Data data) {
        state.histogram.observe(data.dataIterator.next());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(CompareHistogramsWithOtherLibraries.class.getSimpleName()).build();
        new Runner(opt).run();
    }

}
