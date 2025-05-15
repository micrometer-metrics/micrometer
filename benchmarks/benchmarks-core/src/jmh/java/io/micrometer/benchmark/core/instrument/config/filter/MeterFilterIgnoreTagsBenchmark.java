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
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.TimeUnit;

/**
 * Evaluates performance of {@link MeterFilter#commonTags(Iterable)}. To simulate at least
 * a somewhat realistic scenario and prevent false JIT assumptions, the input identifiers
 * have an arbitrary number of tags (0 to 64), having a prominent mode of [supplied].
 */
// Use jvmArgsAppend = "-XX:CompileCommand=dontinline,*.Tags.*" and
// similar wildcards if you need to check clean assembly output
// separately from other code.
@Fork(value = 1)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 54, time = 10, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MeterFilterIgnoreTagsBenchmark {

    private static final int COUNT = BenchmarkSupport.DEFAULT_POOL_SIZE;

    private static final int MASK = BenchmarkSupport.DEFAULT_MASK;

    private static final int SAMPLE_STEP = BenchmarkSupport.SAMPLE_STEP;

    @Param({ "0", "1", "2", "4", "8", "16", "32", "64" })
    public int supplied;

    @Param({ "0", "1", "2", "4", "8", "16", "32", "64" })
    public int ignored;

    private Meter.Id[] samples;

    private MeterFilter[] instances;

    private int instance;

    private int sample;

    @Setup
    public void setUp() {
        samples = FilterBenchmarkSupport.distributed(supplied).limit(COUNT).toArray(Meter.Id[]::new);

        instances = TagsBenchmarkSupport.containers(ignored)
            .map(tags -> tags.stream().map(Tag::getKey).toArray(String[]::new))
            .map(MeterFilter::ignoreTags)
            .limit(COUNT)
            .toArray(MeterFilter[]::new);

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
        BenchmarkSupport.run(MeterFilterIgnoreTagsBenchmark.class);
    }

}
