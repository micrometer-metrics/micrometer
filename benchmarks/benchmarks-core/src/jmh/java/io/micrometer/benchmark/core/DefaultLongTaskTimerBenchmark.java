/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class DefaultLongTaskTimerBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(DefaultLongTaskTimerBenchmark.class.getSimpleName()).build();

        new Runner(opt).run();
    }

    private static final Random random = new Random();

    @Param({ "10000", "100000" })
    int activeSampleCount;

    private MockClock clock;

    private DefaultLongTaskTimer longTaskTimer;

    private LongTaskTimer.Sample randomSample;

    @Setup(Level.Invocation)
    public void setup() {
        clock = new MockClock();
        longTaskTimer = new DefaultLongTaskTimer(
                new Meter.Id("ltt", Tags.empty(), TimeUnit.MILLISECONDS.toString().toLowerCase(Locale.ROOT), null,
                        Meter.Type.LONG_TASK_TIMER),
                clock, TimeUnit.MILLISECONDS, DistributionStatisticConfig.DEFAULT, false);
        int randomIndex = random.nextInt(activeSampleCount);
        // start some samples for benchmarking start/stop with active samples
        IntStream.range(0, activeSampleCount).forEach(offset -> {
            clock.add(offset, TimeUnit.MILLISECONDS);
            LongTaskTimer.Sample sample = longTaskTimer.start();
            if (offset == randomIndex)
                randomSample = sample;
        });
        clock.add(1, TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public LongTaskTimer.Sample start() {
        return longTaskTimer.start();
    }

    @Benchmark
    public long stopRandom() {
        return randomSample.stop();
    }

}
