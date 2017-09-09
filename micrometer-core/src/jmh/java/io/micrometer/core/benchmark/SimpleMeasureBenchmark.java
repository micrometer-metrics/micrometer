/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.benchmark;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * @author Dmitry Poluyanov
 * @since 22.07.17
 */
@Warmup(iterations = 10)
@Fork(jvmArgs = {/*"-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps", */"-Xmx8m"})
@Measurement(iterations = 10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class SimpleMeasureBenchmark {
    private Timer timer;
    private LongTaskTimer longTaskTimer;
    private Counter counter;
    private Gauge gauge;
    private DistributionSummary distributionSummary;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SimpleMeasureBenchmark.class.getSimpleName())
                .warmupIterations(20)
                .measurementIterations(30)
                .mode(Mode.Throughput)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    @Setup
    public void setup() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        timer = registry.timer("timer");
        longTaskTimer = registry.more().longTaskTimer(registry.createId("longTask", emptyList(), null));
        counter = registry.counter("counter");

        List<Integer> testListReference = Arrays.asList(1, 2);
        gauge = registry.gauge(registry.createId("gauge", emptyList(), null), testListReference, List::size);

        distributionSummary = registry.summary("summary");
    }

    @Benchmark
    public Iterable<io.micrometer.core.instrument.Measurement> timerMeasure() {
        return timer.measure();
    }

    @Benchmark
    public Iterable<io.micrometer.core.instrument.Measurement> longTaskTimerMeasure() {
        return longTaskTimer.measure();
    }

    @Benchmark
    public Iterable<io.micrometer.core.instrument.Measurement> counterMeasure() {
        return counter.measure();
    }

    @Benchmark
    public Iterable<io.micrometer.core.instrument.Measurement> gaugeMeasure() {
        return gauge.measure();
    }

    @Benchmark
    public Iterable<io.micrometer.core.instrument.Measurement> distributionSummaryMeasure() {
        return distributionSummary.measure();
    }
}
