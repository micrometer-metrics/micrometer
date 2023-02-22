/*
 * Copyright 2022 VMware, Inc.
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

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ObservationBenchmark {

    SimpleMeterRegistry meterRegistry;

    ObservationRegistry observationRegistry;

    ObservationRegistry noopRegistry;

    Timer timer;

    @Setup
    public void setup() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.timer = Timer.builder("cached.timer").tag("abc", "123").register(meterRegistry);
        this.observationRegistry = ObservationRegistry.create();
        this.observationRegistry.observationConfig()
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        this.noopRegistry = ObservationRegistry.create();
    }

    @TearDown
    public void tearDown() {
        System.out.println("Meters:");
        System.out.println(meterRegistry.getMetersAsString());
    }

    @Benchmark
    public void baseline() {
        // this method was intentionally left blank.
    }

    @Benchmark
    public long cachedTimerWithLong() { // Dynamic tags don't work
        long start = System.nanoTime();
        long duration = System.nanoTime() - start;
        timer.record(duration, TimeUnit.NANOSECONDS);

        return duration;
    }

    @Benchmark
    public long cachedTimerWithSample() { // Dynamic tags don't work
        Timer.Sample sample = Timer.start(meterRegistry);
        return sample.stop(timer);
    }

    @Benchmark
    public long builtTimerWithSample() {
        Timer.Sample sample = Timer.start(meterRegistry);
        return sample.stop(Timer.builder("built.timer").tag("abc", "123").register(meterRegistry));
    }

    @Benchmark
    public long builtTimerAndLongTaskTimer() {
        LongTaskTimer.Sample longTaskSample = LongTaskTimer.builder("built.timer.active")
            .tag("abc", "123")
            .register(meterRegistry)
            .start();
        Timer.Sample sample = Timer.start(meterRegistry);

        long latencyWithTimer = sample.stop(Timer.builder("built.timer").tag("abc", "123").register(meterRegistry));
        long latencyWithLongTaskTimer = longTaskSample.stop();

        return latencyWithTimer + latencyWithLongTaskTimer;
    }

    @Threads(1)
    @Benchmark
    public Observation observationWithoutThreadContention() {
        Observation observation = Observation.createNotStarted("test.obs", observationRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    @Benchmark
    public Observation observation() {
        Observation observation = Observation.createNotStarted("test.obs", observationRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    @Benchmark
    public ObservationOrTimerCompatibleInstrumentation<Observation.Context> observationOrTimer() {
        ObservationOrTimerCompatibleInstrumentation<Observation.Context> instrumentation = ObservationOrTimerCompatibleInstrumentation
            .start(meterRegistry, noopRegistry, null, null, null);
        instrumentation.stop("test.obs-or-timer", null, () -> Tags.of("abc", "123"));

        return instrumentation;
    }

    @Benchmark
    public Observation noopObservation() {
        // This might not measure anything if JIT figures it out that the registry is
        // always noop
        Observation observation = Observation.createNotStarted("test.obs", noopRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(ObservationBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
