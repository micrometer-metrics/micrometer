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
package io.micrometer.benchmark.core.instrument;

import io.micrometer.benchmark.BenchmarkSupport;
import io.micrometer.benchmark.core.instrument.config.filter.FilterBenchmarkSupport;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MeterBenchmarks {

    private MeterBenchmarks() {
    }

    @Fork(value = 1)
    @Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 54, time = 10, timeUnit = TimeUnit.SECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    public static class GetTags {

        private static final int COUNT = BenchmarkSupport.DEFAULT_POOL_SIZE;

        private static final int MASK = BenchmarkSupport.DEFAULT_MASK;

        @Param({ "0", "1", "2", "4", "8", "16", "32", "64" })
        public int mode;

        private Meter.Id[] identifiers;

        private int iteration;

        @Setup
        public void setUp() {
            identifiers = FilterBenchmarkSupport.distributed(mode).limit(COUNT).toArray(Meter.Id[]::new);
        }

        @Benchmark
        public List<Tag> baseline() {
            return identifiers[iteration++ & MASK].getTags();
        }

        public static void main(String[] args) throws RunnerException {
            BenchmarkSupport.run(GetTags.class);
        }

    }

    public static void main(String[] args) throws RunnerException {
        BenchmarkSupport.run(MeterBenchmarks.class);
    }

}
