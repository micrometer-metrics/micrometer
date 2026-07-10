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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// Use jvmArgsAppend = "-XX:CompileCommand=dontinline,*.Tags.*" if you
// need to check clean filter output separately from other code.
@Fork(1)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 54, time = 10, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MeterFilterReplaceTagValuesBenchmark {

    private static final int POOL_SIZE = BenchmarkSupport.DEFAULT_POOL_SIZE;

    private static final int MASK = BenchmarkSupport.DEFAULT_MASK;

    private static final int SAMPLE_STEP = BenchmarkSupport.SAMPLE_STEP;

    @Param({ "0", "1", "2", "4", "8", "16", "32", "64" })
    public int supplied;

    private Meter.Id[] samples;

    private MeterFilter[] instances;

    private int instance;

    private int sample;

    @Setup
    public void setUp() {
        samples = FilterBenchmarkSupport.distributed(supplied).limit(POOL_SIZE).toArray(Meter.Id[]::new);

        instances = Stream.generate(() -> {
            String matcher = TagsBenchmarkSupport.sample().getKey();
            String[] values = TagsBenchmarkSupport.samples().map(Tag::getValue).toArray(String[]::new);
            BenchmarkSupport.shuffle(values);

            String[] exceptions = Arrays.stream(values)
                // 1/4 chance to hit an exception
                .limit(TagsBenchmarkSupport.COUNT / 4)
                .toArray(String[]::new);

            BenchmarkSupport.shuffle(exceptions);
            return MeterFilter.replaceTagValues(matcher, any -> TagsBenchmarkSupport.sample().getValue(), exceptions);
        }).limit(POOL_SIZE).toArray(MeterFilter[]::new);

        // Generally unnecessary, but just to be able to simulate the
        // benchmark in debugger if needed
        instance = 0;
        sample = 0;
    }

    @Benchmark
    public Meter.Id baseline() {
        int index = sample += SAMPLE_STEP;

        return instances[instance++ & MASK].map(samples[index & MASK]);
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(MeterFilterReplaceTagValuesBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
