/*
 * Copyright 2026 VMware, Inc.
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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class CompositeMeterRemovalBenchmark {

    @Param({ "10", "10000" })
    int sharedMeterCount;

    CompositeMeterRegistry composite;

    Meter meterToReplace;

    int nextTagValue;

    @Setup
    public void setup() {
        SimpleMeterRegistry child = new SimpleMeterRegistry();
        child.config().meterFilter(MeterFilter.ignoreTags("key"));
        composite = new CompositeMeterRegistry();
        composite.add(child);
        for (int i = 0; i < sharedMeterCount; i++) {
            meterToReplace = composite.counter("counter", "key", String.valueOf(i));
        }
        nextTagValue = sharedMeterCount;
    }

    @Benchmark
    public Meter removeAndReplaceSharedChildOwner() {
        Meter removed = composite.remove(meterToReplace);
        meterToReplace = composite.counter("counter", "key", String.valueOf(nextTagValue++));
        return removed;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(CompositeMeterRemovalBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
