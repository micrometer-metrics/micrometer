/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.benchmark.core.instrument.config.filter;

import io.micrometer.benchmark.BenchmarkSupport;
import io.micrometer.benchmark.core.instrument.TagsBenchmarkSupport;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// Use jvmArgsAppend = "-XX:CompileCommand=dontinline,*.Tags.*" and
// similar wildcards if you need to check clean assembly output
// separately from other code.
@Fork(1)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 54, time = 10, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MeterFilterRenameTagBenchmark {

    private static final int SAMPLE_STEP = BenchmarkSupport.SAMPLE_STEP;

    /**
     * Mode of the number of supplied tags.
     */
    @Param({ "0", "1", "2", "4", "8", "16", "32", "64" })
    public int supplied;

    private MeterFilter[] instances;

    private Meter.Id[] samples;

    private int instance;

    private int sample;

    private int instanceMask;

    private int sampleMask;

    // Having four variations will result in 1/4 probability of hitting
    // the prefix. It is likely to be smaller in real life, but smaller
    // values would render the difference less distinguishable.
    // While this says "average latency will be low", the prefix
    // matching would be positive in very specific paths that would
    // suffer every time.
    private static Stream<String> alphabet() {
        return Stream.of("alfa", "bravo", "charlie", "delta");
    }

    private static Stream<String> names() {
        // Making as many variations as possible, 4^4 = 256
        return alphabet().flatMap(prefix -> alphabet().map(suffix -> prefix + '.' + suffix))
            .flatMap(prefix -> alphabet().map(suffix -> prefix + '.' + suffix))
            .flatMap(prefix -> alphabet().map(suffix -> prefix + '.' + suffix));
    }

    @Setup
    public void setUp() {
        instances = alphabet()
            .flatMap(prefix -> names()
                .map(replacement -> MeterFilter.renameTag(prefix, TagsBenchmarkSupport.sample().getKey(), replacement)))
            .toArray(MeterFilter[]::new);

        samples = names()
            // This will blow L1, but we really need to make sure JIT doesn't
            // see the same string going in, and we're interested mostly in the
            // trend
            .flatMap(name -> FilterBenchmarkSupport.distributed(supplied).limit(256).map(id -> id.withName(name)))
            .toArray(Meter.Id[]::new);

        if (Integer.bitCount(samples.length) != 1) {
            throw new IllegalStateException("Number of samples isn't a power of 2: " + samples.length);
        }

        if (Integer.bitCount(instances.length) != 1) {
            throw new IllegalStateException("Number of instances isn't a power of 2: " + instances.length);
        }

        // Fuzzing to prevent any kind of patterns and repeated names/tags
        BenchmarkSupport.shuffle(samples);
        BenchmarkSupport.shuffle(instances);

        // Generally unnecessary, but just to be able to simulate the
        // benchmark in debugger if needed
        instance = 0;
        sample = 0;

        // this could have been a constant, but it's too easy to fail here
        sampleMask = samples.length - 1;
        instanceMask = instances.length - 1;
    }

    @Benchmark
    public Meter.Id baseline() {
        int index = sample += SAMPLE_STEP;

        return instances[instance++ & instanceMask].map(samples[index & sampleMask]);
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(MeterFilterRenameTagBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
