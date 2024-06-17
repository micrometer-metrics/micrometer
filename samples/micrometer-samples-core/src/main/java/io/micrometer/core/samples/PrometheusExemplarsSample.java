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
package io.micrometer.core.samples;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.tracer.common.SpanContext;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static io.prometheus.client.exporter.common.TextFormat.CONTENT_TYPE_OPENMETRICS_100;

public class PrometheusExemplarsSample {

    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT,
            new PrometheusRegistry(), Clock.SYSTEM, new TestSpanContext());

    public static void main(String[] args) throws InterruptedException {
        Counter counter = registry.counter("test.counter");
        counter.increment();

        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(registry);
        timer.record(Duration.ofNanos(1_000 * 100));
        sleepToAvoidRateLimiting();
        timer.record(Duration.ofMillis(2));
        sleepToAvoidRateLimiting();
        timer.record(Duration.ofMillis(100));
        sleepToAvoidRateLimiting();
        timer.record(Duration.ofSeconds(60));

        DistributionSummary distributionSummary = DistributionSummary.builder("test.distribution")
            .publishPercentileHistogram()
            .register(registry);
        distributionSummary.record(0.15);
        sleepToAvoidRateLimiting();
        distributionSummary.record(15);
        sleepToAvoidRateLimiting();
        distributionSummary.record(5E18);

        System.out.println(registry.scrape(CONTENT_TYPE_OPENMETRICS_100));
    }

    static void sleepToAvoidRateLimiting() {
        try {
            // sleeping 100ms since the sample interval limit is 90ms
            Thread.sleep(100);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static class TestSpanContext implements SpanContext {

        private final AtomicLong count = new AtomicLong();

        @Override
        public String getCurrentTraceId() {
            return "42";
        }

        @Override
        public String getCurrentSpanId() {
            return String.valueOf(count.incrementAndGet());
        }

        @Override
        public boolean isCurrentSpanSampled() {
            return true;
        }

        @Override
        public void markCurrentSpanAsExemplar() {
        }

    }

}
