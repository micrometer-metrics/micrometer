/*
 * Copyright 2023 VMware, Inc.
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

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
//CHECKSTYLE:OFF
import com.google.common.collect.Iterators;
////CHECKSTYLE:ON
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.HistogramFlavor;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * @author Lenin Jaganathan
 */
@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(16)
public class CompareOTLPHistograms {

    @State(Scope.Thread)
    public static class Data {

        Iterator<Long> dataIterator;

        @Setup(Level.Iteration)
        public void setup() {
            final Random r = new Random(1234567891L);
            dataIterator = Iterators.cycle(Stream.generate(() -> {
                long randomNumber;
                do {
                    randomNumber = Math.round(Math.exp(2.0 + r.nextGaussian()));
                }
                while (randomNumber < 1 || randomNumber > 60000);
                return randomNumber;
            }).limit(1048576).collect(Collectors.toList()));
        }

    }

    @State(Scope.Benchmark)
    public static class DistributionsWithoutHistogramCumulative {

        MeterRegistry registry;

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry();
            distributionSummary = DistributionSummary.builder("ds").register(registry);
            timer = Timer.builder("timer").register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @State(Scope.Benchmark)
    public static class DistributionsWithoutHistogramDelta {

        OtlpConfig otlpConfig = new OtlpConfig() {

            @Override
            public AggregationTemporality aggregationTemporality() {
                return AggregationTemporality.DELTA;
            }

            @Nullable
            @Override
            public String get(final String key) {
                return null;
            }
        };

        MeterRegistry registry;

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
            distributionSummary = DistributionSummary.builder("ds").register(registry);
            timer = Timer.builder("timer").register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @State(Scope.Benchmark)
    public static class ExplicitBucketHistogramCumulative {

        MeterRegistry registry;

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry();
            distributionSummary = DistributionSummary.builder("ds").publishPercentileHistogram().register(registry);
            timer = Timer.builder("timer").publishPercentileHistogram().register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @State(Scope.Benchmark)
    public static class ExplicitBucketHistogramDelta {

        OtlpConfig otlpConfig = new OtlpConfig() {

            @Override
            public AggregationTemporality aggregationTemporality() {
                return AggregationTemporality.DELTA;
            }

            @Nullable
            @Override
            public String get(final String key) {
                return null;
            }
        };

        MeterRegistry registry;

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
            distributionSummary = DistributionSummary.builder("ds").publishPercentileHistogram().register(registry);
            timer = Timer.builder("timer").publishPercentileHistogram().register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @State(Scope.Benchmark)
    public static class ExponentialHistogramCumulative {

        MeterRegistry registry;

        OtlpConfig otlpConfig = new OtlpConfig() {
            @Override
            public HistogramFlavor histogramFlavor() {
                return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Nullable
            @Override
            public String get(final String key) {
                return null;
            }
        };

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
            distributionSummary = DistributionSummary.builder("ds").publishPercentileHistogram().register(registry);
            timer = Timer.builder("timer").publishPercentileHistogram().register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @State(Scope.Benchmark)
    public static class ExponentialHistogramDelta {

        MeterRegistry registry;

        OtlpConfig otlpConfig = new OtlpConfig() {

            @Override
            public AggregationTemporality aggregationTemporality() {
                return AggregationTemporality.DELTA;
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Nullable
            @Override
            public String get(final String key) {
                return null;
            }
        };

        Timer timer;

        DistributionSummary distributionSummary;

        @Setup(Level.Iteration)
        public void setup() {
            registry = new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
            distributionSummary = DistributionSummary.builder("ds").publishPercentileHistogram().register(registry);
            timer = Timer.builder("timer").publishPercentileHistogram().register(registry);
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(distributionSummary.takeSnapshot());
        }

    }

    @Benchmark
    public void otlpCumulativeDs(DistributionsWithoutHistogramCumulative state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void otlpDeltaDs(DistributionsWithoutHistogramDelta state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void otlpCumulativeExplicitBucketHistogramDs(ExplicitBucketHistogramCumulative state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void otlpDeltaExplicitBucketHistogramDs(ExplicitBucketHistogramDelta state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void oltpCumulativeExponentialHistogramDs(ExponentialHistogramCumulative state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void oltpDeltaExponentialHistogramDs(ExponentialHistogramDelta state, Data data) {
        state.distributionSummary.record(data.dataIterator.next());
    }

    @Benchmark
    public void otlpCumulativeTimer(DistributionsWithoutHistogramCumulative state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public void otlpDeltaTimer(DistributionsWithoutHistogramDelta state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public void otlpCumulativeExplicitBucketHistogramTimer(ExplicitBucketHistogramCumulative state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public void otlpDeltaExplicitBucketHistogramTimer(ExplicitBucketHistogramDelta state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public void oltpCumulativeExponentialHistogramTimer(ExponentialHistogramCumulative state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public void oltpDeltaExponentialHistogramTimer(ExponentialHistogramDelta state, Data data) {
        state.timer.record(data.dataIterator.next(), TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(CompareOTLPHistograms.class.getSimpleName()).build();
        new Runner(opt).run();
    }

}
