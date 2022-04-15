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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import sun.management.spi.PlatformMBeanProvider;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpMeterRegistryTest {

    MockClock clock = new MockClock();
    OtlpMeterRegistry registry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, clock);

    @Test
    void gauge() {
        Gauge cpus = Gauge.builder("cpus", ManagementFactory.getOperatingSystemMXBean(), OperatingSystemMXBean::getAvailableProcessors).register(registry);
        assertThat(registry.writeGauge(cpus).toString())
                .matches("name: \"cpus\"\n" +
                        "gauge \\{\n" +
                        "  data_points \\{\n" +
                        "    time_unix_nano: 1000000\n" +
                        "    as_double: \\d+\\.0\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        assertThat(registry.writeGauge(timeGauge).toString())
                .isEqualTo("name: \"gauge.time\"\n" +
                        "unit: \"milliseconds\"\n" +
                        "gauge {\n" +
                        "  data_points {\n" +
                        "    time_unix_nano: 1000000\n" +
                        "    as_double: 0.024\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    void counter() {
        Counter counter = registry.counter("log.event");
        counter.increment();
        counter.increment();
        clock.add(OtlpConfig.DEFAULT.step());
        counter.increment();
        assertThat(registry.writeCounter(counter).toString())
                .isEqualTo("name: \"log.event\"\n" +
                        "sum {\n" +
                        "  data_points {\n" +
                        "    start_time_unix_nano: 1000000\n" +
                        "    time_unix_nano: 60001000000\n" +
                        "    as_double: 3.0\n" +
                        "  }\n" +
                        "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" +
                        "  is_monotonic: true\n" +
                        "}\n");
    }

    @Test
    void functionCounter() {
        FunctionCounter counter = FunctionCounter.builder("jvm.compilation.time", ManagementFactory.getCompilationMXBean(), CompilationMXBean::getTotalCompilationTime)
                .baseUnit("milliseconds")
                .register(registry);

        assertThat(registry.writeFunctionCounter(counter).toString())
                .matches("name: \"jvm.compilation.time\"\n" +
                        "unit: \"milliseconds\"\n" +
                        "sum \\{\n" +
                        "  data_points \\{\n" +
                        "    start_time_unix_nano: 1000000\n" +
                        "    time_unix_nano: 1000000\n" +
                        "    as_double: \\d+\\.0\n" +
                        "  }\n" +
                        "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" +
                        "  is_monotonic: true\n" +
                        "}\n");
    }

    @Test
    void timer() {
        Timer timer = Timer.builder("http.client.requests").description("timing http client requests").register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        clock.add(OtlpConfig.DEFAULT.step());
        timer.record(4, TimeUnit.MILLISECONDS);
        assertThat(registry.writeHistogramSupport(timer).toString())
                .isEqualTo("name: \"http.client.requests\"\n" +
                        "description: \"timing http client requests\"\n" +
                        "unit: \"milliseconds\"\n" +
                        "histogram {\n" +
                        "  data_points {\n" +
                        "    time_unix_nano: 60001000000\n" +
                        "    count: 4\n" +
                        "    sum: 202.0\n" +
                        "  }\n" +
                        "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" +
                        "}\n");
    }

    @Test
    void timerWithHistogram() {
        Timer timer = Timer.builder("http.client.requests").publishPercentileHistogram().register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        clock.add(OtlpConfig.DEFAULT.step());
        timer.record(4, TimeUnit.MILLISECONDS);

        System.out.println(registry.writeHistogramSupport(timer));
    }

    @Test
    void timerWithPercentiles() {
        Timer timer = Timer.builder("http.client.requests").publishPercentiles(0.5, 0.9, 0.99).register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        assertThat(registry.writeHistogramSupport(timer).toString())
                .isEqualTo("name: \"http.client.requests\"\n" +
                        "unit: \"milliseconds\"\n" +
                        "summary {\n" +
                        "  data_points {\n" +
                        "    time_unix_nano: 1000000\n" +
                        "    count: 3\n" +
                        "    sum: 1.98E8\n" +
                        "    quantile_values {\n" +
                        "      quantile: 0.5\n" +
                        "      value: 79.167488\n" +
                        "    }\n" +
                        "    quantile_values {\n" +
                        "      quantile: 0.9\n" +
                        "      value: 112.72192\n" +
                        "    }\n" +
                        "    quantile_values {\n" +
                        "      quantile: 0.99\n" +
                        "      value: 112.72192\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    void functionTimer() {
        FunctionTimer functionTimer = FunctionTimer.builder("function.timer", this, o -> 5, o -> 127, TimeUnit.MILLISECONDS).register(registry);

        assertThat(registry.writeFunctionTimer(functionTimer).toString())
                .isEqualTo("name: \"function.timer\"\n" +
                        "unit: \"milliseconds\"\n" +
                        "histogram {\n" +
                        "  data_points {\n" +
                        "    time_unix_nano: 1000000\n" +
                        "    count: 5\n" +
                        "    sum: 127.0\n" +
                        "  }\n" +
                        "}\n");
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder("http.request.size").baseUnit("bytes").register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(OtlpConfig.DEFAULT.step());
        size.record(204);

        assertThat(registry.writeHistogramSupport(size).toString())
                .isEqualTo("name: \"http.request.size\"\n" +
                        "unit: \"bytes\"\n" +
                        "histogram {\n" +
                        "  data_points {\n" +
                        "    time_unix_nano: 60001000000\n" +
                        "    count: 4\n" +
                        "    sum: 2552.0\n" +
                        "  }\n" +
                        "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" +
                        "}\n");
    }

    @Test
    void distributionSummaryWithHistogramBuckets() {
        DistributionSummary size = DistributionSummary.builder("http.request.size").baseUnit("bytes").publishPercentileHistogram().register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(OtlpConfig.DEFAULT.step());
        size.record(204);

        System.out.println(registry.writeHistogramSupport(size));
    }
}
