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
package io.micrometer.prometheusmetrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Fork(value = 1, jvmArgs = { "-Xms8g", "-Xmx8g" })
@Warmup(iterations = 5)
@Measurement(iterations = 15)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MicrometerCollectorBenchmark {

    @Benchmark
    public MetricSnapshots collect(CollectorState state) {
        return state.collector.collect();
    }

    @State(Scope.Benchmark)
    public static class CollectorState {

        @Param({ "1000", "10000", "100000" })
        public int children;

        MicrometerCollector collector;

        @Setup(Level.Trial)
        public void setup() {
            final NamingConvention convention = NamingConvention.snakeCase;
            final Meter.Id id = new Meter.Id("micrometer.collector.benchmark", Tags.of("tag", "value"), null, "test",
                    Meter.Type.COUNTER);
            collector = new MicrometerCollector(id.getConventionName(convention), id, convention);

            for (int i = 0; i < children; i++) {
                final List<String> tagValues = List.of(Integer.toString(i));
                collector.add(tagValues, counterChild(id, i, tagValues));
            }
        }

        // The MicrometerCollector.Child returned here should closely resemble what
        // PrometheusMeterRegistry adds to the collector and is intentionally left
        // unoptimized.
        private static MicrometerCollector.Child counterChild(Meter.Id id, int childNumber, List<String> tagValues) {
            final PrometheusCounter counter = new PrometheusCounter(id);
            counter.increment(childNumber);

            return (conventionName,
                    tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                            family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                            new MetricMetadata(conventionName, "test", null), new CounterDataPointSnapshot(
                                    counter.count(), Labels.of(tagKeys, tagValues), counter.exemplar(), childNumber)));
        }

    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(MicrometerCollectorBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
