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
package io.micrometer.core.samples;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.*;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class OtlpExemplarsSample {

    private static final OtlpConfig config = new OtlpConfig() {
        @Override
        public Duration step() {
            return Duration.ofSeconds(10);
        }

        @Override
        public AggregationTemporality aggregationTemporality() {
            return AggregationTemporality.CUMULATIVE;
        }

        @Override
        public @Nullable String get(String key) {
            return null;
        }
    };

    private static final MeterRegistry registry = OtlpMeterRegistry.builder(config)
        .metricsSender(new TestMetricsSender())
        .exemplarContextProvider(new TestExemplarContextProvider())
        .build();

    public static void main(String[] args) throws InterruptedException {
        for (int i = 1; i <= 25; i++) {
            System.out.print(String.format("%02d ", i));
            registry.counter("test.counter").increment();

            // Timer.builder("test.timer")
            // .publishPercentileHistogram()
            // .serviceLevelObjectives(Duration.ofMillis(100))
            // .register(registry)
            // .record(Duration.ofMillis(i * 10L));

            // DistributionSummary.builder("test.ds")
            // .publishPercentileHistogram()
            // .serviceLevelObjectives(100)
            // .register(registry)
            // .record(i * 10.0);

            Thread.sleep(1_000);
        }
        registry.close();
    }

    static class TestExemplarContextProvider implements ExemplarContextProvider {

        private final AtomicLong counter = new AtomicLong(1001);

        @Override
        public OtlpExemplarContext getExemplarContext() {
            String suffix = String.valueOf(counter.getAndIncrement());
            OtlpExemplarContext context = new OtlpExemplarContext("66fd7359621b3043e2321480aaaa" + suffix,
                    "e2321480aaaa" + suffix);
            System.out.println(String.format("%s: %s-%s", Instant.now(), context.getTraceId(), context.getSpanId()));
            return context;
        }

    }

    static class TestMetricsSender implements OtlpMetricsSender {

        @Override
        public void send(Request request) throws Exception {
            System.out.println("Publishing...");
            System.out.println(ExportMetricsServiceRequest.parseFrom(request.getMetricsData()));
        }

    }

}
