/*
 * Copyright 2023 VMware, Inc.
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
import io.micrometer.core.instrument.binder.BaseUnits;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.junit.jupiter.api.Test;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpCumulativeMeterRegistryTest extends OtlpMeterRegistryTest {

    @Override
    protected OtlpConfig otlpConfig() {
        return OtlpConfig.DEFAULT;
    }

    @Override
    OtlpConfig exponentialHistogramOtlpConfig() {
        return new OtlpConfig() {

            @Override
            public HistogramFlavor histogramFlavor() {
                return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Override
            public String get(final String key) {
                return null;
            }
        };
    }

    @Test
    void gauge() {
        Gauge cpus = Gauge
            .builder("cpus", ManagementFactory.getOperatingSystemMXBean(),
                    OperatingSystemMXBean::getAvailableProcessors)
            .register(registry);
        assertThat(writeToMetric(cpus).toString()).matches("name: \"cpus\"\n" + "gauge \\{\n" + "  data_points \\{\n"
                + "    time_unix_nano: 1000000\n" + "    as_double: \\d+\\.0\n" + "  }\n" + "}\n");
    }

    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        assertThat(writeToMetric(timeGauge).toString())
            .isEqualTo("name: \"gauge.time\"\n" + "unit: \"milliseconds\"\n" + "gauge {\n" + "  data_points {\n"
                    + "    time_unix_nano: 1000000\n" + "    as_double: 0.024\n" + "  }\n" + "}\n");
    }

    @Test
    void counter() {
        Counter counter = registry.counter("log.event", "level", "info");
        counter.increment();
        counter.increment();
        clock.add(otlpConfig().step());
        counter.increment();
        assertThat(writeToMetric(counter).toString()).isEqualTo("name: \"log.event\"\n" + "sum {\n"
                + "  data_points {\n" + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 60001000000\n"
                + "    as_double: 3.0\n" + "    attributes {\n" + "      key: \"level\"\n" + "      value {\n"
                + "        string_value: \"info\"\n" + "      }\n" + "    }\n" + "  }\n"
                + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "  is_monotonic: true\n" + "}\n");
    }

    @Test
    void functionCounter() {
        FunctionCounter counter = FunctionCounter
            .builder("jvm.compilation.time", ManagementFactory.getCompilationMXBean(),
                    CompilationMXBean::getTotalCompilationTime)
            .baseUnit("milliseconds")
            .register(registry);

        assertThat(writeToMetric(counter).toString()).matches("name: \"jvm.compilation.time\"\n"
                + "unit: \"milliseconds\"\n" + "sum \\{\n" + "  data_points \\{\n"
                + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 1000000\n" + "    as_double: \\d+\\.0\n"
                + "  }\n" + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "  is_monotonic: true\n"
                + "}\n");
    }

    @Test
    void timer() {
        Timer timer = Timer.builder("web.requests").description("timing web requests").register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        clock.add(otlpConfig().step());
        timer.record(4, TimeUnit.MILLISECONDS);
        assertThat(writeToMetric(timer).toString()).isEqualTo(
                "name: \"web.requests\"\n" + "description: \"timing web requests\"\n" + "unit: \"milliseconds\"\n"
                        + "histogram {\n" + "  data_points {\n" + "    start_time_unix_nano: 1000000\n"
                        + "    time_unix_nano: 60001000000\n" + "    count: 4\n" + "    sum: 202.0\n" + "  }\n"
                        + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n");
    }

    @Test
    void timerWithHistogram() {
        Timer timer = Timer.builder("http.client.requests").publishPercentileHistogram().register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.MINUTES);
        clock.add(otlpConfig().step());
        timer.record(4, TimeUnit.MILLISECONDS);

        assertThat(writeToMetric(timer).toString())
            .isEqualTo("name: \"http.client.requests\"\n" + "unit: \"milliseconds\"\n" + "histogram {\n"
                    + "  data_points {\n" + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 60001000000\n"
                    + "    count: 5\n" + "    sum: 60202.0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 1\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 1\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 1\n" + "    bucket_counts: 1\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                    + "    bucket_counts: 1\n" + "    explicit_bounds: 1.0\n" + "    explicit_bounds: 1.048576\n"
                    + "    explicit_bounds: 1.398101\n" + "    explicit_bounds: 1.747626\n"
                    + "    explicit_bounds: 2.097151\n" + "    explicit_bounds: 2.446676\n"
                    + "    explicit_bounds: 2.796201\n" + "    explicit_bounds: 3.145726\n"
                    + "    explicit_bounds: 3.495251\n" + "    explicit_bounds: 3.844776\n"
                    + "    explicit_bounds: 4.194304\n" + "    explicit_bounds: 5.592405\n"
                    + "    explicit_bounds: 6.990506\n" + "    explicit_bounds: 8.388607\n"
                    + "    explicit_bounds: 9.786708\n" + "    explicit_bounds: 11.184809\n"
                    + "    explicit_bounds: 12.58291\n" + "    explicit_bounds: 13.981011\n"
                    + "    explicit_bounds: 15.379112\n" + "    explicit_bounds: 16.777216\n"
                    + "    explicit_bounds: 22.369621\n" + "    explicit_bounds: 27.962026\n"
                    + "    explicit_bounds: 33.554431\n" + "    explicit_bounds: 39.146836\n"
                    + "    explicit_bounds: 44.739241\n" + "    explicit_bounds: 50.331646\n"
                    + "    explicit_bounds: 55.924051\n" + "    explicit_bounds: 61.516456\n"
                    + "    explicit_bounds: 67.108864\n" + "    explicit_bounds: 89.478485\n"
                    + "    explicit_bounds: 111.848106\n" + "    explicit_bounds: 134.217727\n"
                    + "    explicit_bounds: 156.587348\n" + "    explicit_bounds: 178.956969\n"
                    + "    explicit_bounds: 201.32659\n" + "    explicit_bounds: 223.696211\n"
                    + "    explicit_bounds: 246.065832\n" + "    explicit_bounds: 268.435456\n"
                    + "    explicit_bounds: 357.913941\n" + "    explicit_bounds: 447.392426\n"
                    + "    explicit_bounds: 536.870911\n" + "    explicit_bounds: 626.349396\n"
                    + "    explicit_bounds: 715.827881\n" + "    explicit_bounds: 805.306366\n"
                    + "    explicit_bounds: 894.784851\n" + "    explicit_bounds: 984.263336\n"
                    + "    explicit_bounds: 1073.741824\n" + "    explicit_bounds: 1431.655765\n"
                    + "    explicit_bounds: 1789.569706\n" + "    explicit_bounds: 2147.483647\n"
                    + "    explicit_bounds: 2505.397588\n" + "    explicit_bounds: 2863.311529\n"
                    + "    explicit_bounds: 3221.22547\n" + "    explicit_bounds: 3579.139411\n"
                    + "    explicit_bounds: 3937.053352\n" + "    explicit_bounds: 4294.967296\n"
                    + "    explicit_bounds: 5726.623061\n" + "    explicit_bounds: 7158.278826\n"
                    + "    explicit_bounds: 8589.934591\n" + "    explicit_bounds: 10021.590356\n"
                    + "    explicit_bounds: 11453.246121\n" + "    explicit_bounds: 12884.901886\n"
                    + "    explicit_bounds: 14316.557651\n" + "    explicit_bounds: 15748.213416\n"
                    + "    explicit_bounds: 17179.869184\n" + "    explicit_bounds: 22906.492245\n"
                    + "    explicit_bounds: 28633.115306\n" + "    explicit_bounds: 30000.0\n" + "  }\n"
                    + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n");
    }

    @Test
    void timerWithPercentiles() {
        Timer timer = Timer.builder("service.requests").publishPercentiles(0.5, 0.9, 0.99).register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        assertThat(writeToMetric(timer).toString())
            .isEqualTo("name: \"service.requests\"\n" + "unit: \"milliseconds\"\n" + "summary {\n" + "  data_points {\n"
                    + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 1000000\n" + "    count: 3\n"
                    + "    sum: 198.0\n" + "    quantile_values {\n" + "      quantile: 0.5\n"
                    + "      value: 79.167488\n" + "    }\n" + "    quantile_values {\n" + "      quantile: 0.9\n"
                    + "      value: 112.72192\n" + "    }\n" + "    quantile_values {\n" + "      quantile: 0.99\n"
                    + "      value: 112.72192\n" + "    }\n" + "  }\n" + "}\n");
    }

    @Test
    void functionTimer() {
        FunctionTimer functionTimer = FunctionTimer
            .builder("function.timer", this, o -> 5, o -> 127, TimeUnit.MILLISECONDS)
            .register(registry);

        assertThat(writeToMetric(functionTimer).toString())
            .isEqualTo("name: \"function.timer\"\n" + "unit: \"milliseconds\"\n" + "histogram {\n" + "  data_points {\n"
                    + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 1000000\n" + "    count: 5\n"
                    + "    sum: 127.0\n" + "  }\n" + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n"
                    + "}\n");
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder("http.response.size")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);

        assertThat(writeToMetric(size).toString()).isEqualTo("name: \"http.response.size\"\n" + "unit: \"bytes\"\n"
                + "histogram {\n" + "  data_points {\n" + "    start_time_unix_nano: 1000000\n"
                + "    time_unix_nano: 60001000000\n" + "    count: 4\n" + "    sum: 2552.0\n" + "  }\n"
                + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n");
    }

    @Test
    void distributionSummaryWithHistogram() {
        DistributionSummary size = DistributionSummary.builder("http.request.size")
            .baseUnit(BaseUnits.BYTES)
            .publishPercentileHistogram()
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);

        String expected = "name: \"http.request.size\"\n" + "unit: \"bytes\"\n" + "histogram {\n" + "  data_points {\n"
                + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 60001000000\n" + "    count: 4\n"
                + "    sum: 2552.0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 1\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 1\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 1\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 1\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    bucket_counts: 0\n" + "    bucket_counts: 0\n" + "    bucket_counts: 0\n"
                + "    explicit_bounds: 1.0\n" + "    explicit_bounds: 2.0\n" + "    explicit_bounds: 3.0\n"
                + "    explicit_bounds: 4.0\n" + "    explicit_bounds: 5.0\n" + "    explicit_bounds: 6.0\n"
                + "    explicit_bounds: 7.0\n" + "    explicit_bounds: 8.0\n" + "    explicit_bounds: 9.0\n"
                + "    explicit_bounds: 10.0\n" + "    explicit_bounds: 11.0\n" + "    explicit_bounds: 12.0\n"
                + "    explicit_bounds: 13.0\n" + "    explicit_bounds: 14.0\n" + "    explicit_bounds: 16.0\n"
                + "    explicit_bounds: 21.0\n" + "    explicit_bounds: 26.0\n" + "    explicit_bounds: 31.0\n"
                + "    explicit_bounds: 36.0\n" + "    explicit_bounds: 41.0\n" + "    explicit_bounds: 46.0\n"
                + "    explicit_bounds: 51.0\n" + "    explicit_bounds: 56.0\n" + "    explicit_bounds: 64.0\n"
                + "    explicit_bounds: 85.0\n" + "    explicit_bounds: 106.0\n" + "    explicit_bounds: 127.0\n"
                + "    explicit_bounds: 148.0\n" + "    explicit_bounds: 169.0\n" + "    explicit_bounds: 190.0\n"
                + "    explicit_bounds: 211.0\n" + "    explicit_bounds: 232.0\n" + "    explicit_bounds: 256.0\n"
                + "    explicit_bounds: 341.0\n" + "    explicit_bounds: 426.0\n" + "    explicit_bounds: 511.0\n"
                + "    explicit_bounds: 596.0\n" + "    explicit_bounds: 681.0\n" + "    explicit_bounds: 766.0\n"
                + "    explicit_bounds: 851.0\n" + "    explicit_bounds: 936.0\n" + "    explicit_bounds: 1024.0\n"
                + "    explicit_bounds: 1365.0\n" + "    explicit_bounds: 1706.0\n" + "    explicit_bounds: 2047.0\n"
                + "    explicit_bounds: 2388.0\n" + "    explicit_bounds: 2729.0\n" + "    explicit_bounds: 3070.0\n"
                + "    explicit_bounds: 3411.0\n" + "    explicit_bounds: 3752.0\n" + "    explicit_bounds: 4096.0\n"
                + "    explicit_bounds: 5461.0\n" + "    explicit_bounds: 6826.0\n" + "    explicit_bounds: 8191.0\n"
                + "    explicit_bounds: 9556.0\n" + "    explicit_bounds: 10921.0\n" + "    explicit_bounds: 12286.0\n"
                + "    explicit_bounds: 13651.0\n" + "    explicit_bounds: 15016.0\n" + "    explicit_bounds: 16384.0\n"
                + "    explicit_bounds: 21845.0\n" + "    explicit_bounds: 27306.0\n" + "    explicit_bounds: 32767.0\n"
                + "    explicit_bounds: 38228.0\n" + "    explicit_bounds: 43689.0\n" + "    explicit_bounds: 49150.0\n"
                + "    explicit_bounds: 54611.0\n" + "    explicit_bounds: 60072.0\n" + "    explicit_bounds: 65536.0\n"
                + "    explicit_bounds: 87381.0\n" + "    explicit_bounds: 109226.0\n"
                + "    explicit_bounds: 131071.0\n" + "    explicit_bounds: 152916.0\n"
                + "    explicit_bounds: 174761.0\n" + "    explicit_bounds: 196606.0\n"
                + "    explicit_bounds: 218451.0\n" + "    explicit_bounds: 240296.0\n"
                + "    explicit_bounds: 262144.0\n" + "    explicit_bounds: 349525.0\n"
                + "    explicit_bounds: 436906.0\n" + "    explicit_bounds: 524287.0\n"
                + "    explicit_bounds: 611668.0\n" + "    explicit_bounds: 699049.0\n"
                + "    explicit_bounds: 786430.0\n" + "    explicit_bounds: 873811.0\n"
                + "    explicit_bounds: 961192.0\n" + "    explicit_bounds: 1048576.0\n"
                + "    explicit_bounds: 1398101.0\n" + "    explicit_bounds: 1747626.0\n"
                + "    explicit_bounds: 2097151.0\n" + "    explicit_bounds: 2446676.0\n"
                + "    explicit_bounds: 2796201.0\n" + "    explicit_bounds: 3145726.0\n"
                + "    explicit_bounds: 3495251.0\n" + "    explicit_bounds: 3844776.0\n"
                + "    explicit_bounds: 4194304.0\n" + "    explicit_bounds: 5592405.0\n"
                + "    explicit_bounds: 6990506.0\n" + "    explicit_bounds: 8388607.0\n"
                + "    explicit_bounds: 9786708.0\n" + "    explicit_bounds: 1.1184809E7\n"
                + "    explicit_bounds: 1.258291E7\n" + "    explicit_bounds: 1.3981011E7\n"
                + "    explicit_bounds: 1.5379112E7\n" + "    explicit_bounds: 1.6777216E7\n"
                + "    explicit_bounds: 2.2369621E7\n" + "    explicit_bounds: 2.7962026E7\n"
                + "    explicit_bounds: 3.3554431E7\n" + "    explicit_bounds: 3.9146836E7\n"
                + "    explicit_bounds: 4.4739241E7\n" + "    explicit_bounds: 5.0331646E7\n"
                + "    explicit_bounds: 5.5924051E7\n" + "    explicit_bounds: 6.1516456E7\n"
                + "    explicit_bounds: 6.7108864E7\n" + "    explicit_bounds: 8.9478485E7\n"
                + "    explicit_bounds: 1.11848106E8\n" + "    explicit_bounds: 1.34217727E8\n"
                + "    explicit_bounds: 1.56587348E8\n" + "    explicit_bounds: 1.78956969E8\n"
                + "    explicit_bounds: 2.0132659E8\n" + "    explicit_bounds: 2.23696211E8\n"
                + "    explicit_bounds: 2.46065832E8\n" + "    explicit_bounds: 2.68435456E8\n"
                + "    explicit_bounds: 3.57913941E8\n" + "    explicit_bounds: 4.47392426E8\n"
                + "    explicit_bounds: 5.36870911E8\n" + "    explicit_bounds: 6.26349396E8\n"
                + "    explicit_bounds: 7.15827881E8\n" + "    explicit_bounds: 8.05306366E8\n"
                + "    explicit_bounds: 8.94784851E8\n" + "    explicit_bounds: 9.84263336E8\n"
                + "    explicit_bounds: 1.073741824E9\n" + "    explicit_bounds: 1.431655765E9\n"
                + "    explicit_bounds: 1.789569706E9\n" + "    explicit_bounds: 2.147483647E9\n"
                + "    explicit_bounds: 2.505397588E9\n" + "    explicit_bounds: 2.863311529E9\n"
                + "    explicit_bounds: 3.22122547E9\n" + "    explicit_bounds: 3.579139411E9\n"
                + "    explicit_bounds: 3.937053352E9\n" + "    explicit_bounds: 4.294967296E9\n"
                + "    explicit_bounds: 5.726623061E9\n" + "    explicit_bounds: 7.158278826E9\n"
                + "    explicit_bounds: 8.589934591E9\n" + "    explicit_bounds: 1.0021590356E10\n"
                + "    explicit_bounds: 1.1453246121E10\n" + "    explicit_bounds: 1.2884901886E10\n"
                + "    explicit_bounds: 1.4316557651E10\n" + "    explicit_bounds: 1.5748213416E10\n"
                + "    explicit_bounds: 1.7179869184E10\n" + "    explicit_bounds: 2.2906492245E10\n"
                + "    explicit_bounds: 2.8633115306E10\n" + "    explicit_bounds: 3.4359738367E10\n"
                + "    explicit_bounds: 4.0086361428E10\n" + "    explicit_bounds: 4.5812984489E10\n"
                + "    explicit_bounds: 5.153960755E10\n" + "    explicit_bounds: 5.7266230611E10\n"
                + "    explicit_bounds: 6.2992853672E10\n" + "    explicit_bounds: 6.8719476736E10\n"
                + "    explicit_bounds: 9.1625968981E10\n" + "    explicit_bounds: 1.14532461226E11\n"
                + "    explicit_bounds: 1.37438953471E11\n" + "    explicit_bounds: 1.60345445716E11\n"
                + "    explicit_bounds: 1.83251937961E11\n" + "    explicit_bounds: 2.06158430206E11\n"
                + "    explicit_bounds: 2.29064922451E11\n" + "    explicit_bounds: 2.51971414696E11\n"
                + "    explicit_bounds: 2.74877906944E11\n" + "    explicit_bounds: 3.66503875925E11\n"
                + "    explicit_bounds: 4.58129844906E11\n" + "    explicit_bounds: 5.49755813887E11\n"
                + "    explicit_bounds: 6.41381782868E11\n" + "    explicit_bounds: 7.33007751849E11\n"
                + "    explicit_bounds: 8.2463372083E11\n" + "    explicit_bounds: 9.16259689811E11\n"
                + "    explicit_bounds: 1.007885658792E12\n" + "    explicit_bounds: 1.099511627776E12\n"
                + "    explicit_bounds: 1.466015503701E12\n" + "    explicit_bounds: 1.832519379626E12\n"
                + "    explicit_bounds: 2.199023255551E12\n" + "    explicit_bounds: 2.565527131476E12\n"
                + "    explicit_bounds: 2.932031007401E12\n" + "    explicit_bounds: 3.298534883326E12\n"
                + "    explicit_bounds: 3.665038759251E12\n" + "    explicit_bounds: 4.031542635176E12\n"
                + "    explicit_bounds: 4.398046511104E12\n" + "    explicit_bounds: 5.864062014805E12\n"
                + "    explicit_bounds: 7.330077518506E12\n" + "    explicit_bounds: 8.796093022207E12\n"
                + "    explicit_bounds: 1.0262108525908E13\n" + "    explicit_bounds: 1.1728124029609E13\n"
                + "    explicit_bounds: 1.319413953331E13\n" + "    explicit_bounds: 1.4660155037011E13\n"
                + "    explicit_bounds: 1.6126170540712E13\n" + "    explicit_bounds: 1.7592186044416E13\n"
                + "    explicit_bounds: 2.3456248059221E13\n" + "    explicit_bounds: 2.9320310074026E13\n"
                + "    explicit_bounds: 3.5184372088831E13\n" + "    explicit_bounds: 4.1048434103636E13\n"
                + "    explicit_bounds: 4.6912496118441E13\n" + "    explicit_bounds: 5.2776558133246E13\n"
                + "    explicit_bounds: 5.8640620148051E13\n" + "    explicit_bounds: 6.4504682162856E13\n"
                + "    explicit_bounds: 7.0368744177664E13\n" + "    explicit_bounds: 9.3824992236885E13\n"
                + "    explicit_bounds: 1.17281240296106E14\n" + "    explicit_bounds: 1.40737488355327E14\n"
                + "    explicit_bounds: 1.64193736414548E14\n" + "    explicit_bounds: 1.87649984473769E14\n"
                + "    explicit_bounds: 2.1110623253299E14\n" + "    explicit_bounds: 2.34562480592211E14\n"
                + "    explicit_bounds: 2.58018728651432E14\n" + "    explicit_bounds: 2.81474976710656E14\n"
                + "    explicit_bounds: 3.75299968947541E14\n" + "    explicit_bounds: 4.69124961184426E14\n"
                + "    explicit_bounds: 5.62949953421311E14\n" + "    explicit_bounds: 6.56774945658196E14\n"
                + "    explicit_bounds: 7.50599937895081E14\n" + "    explicit_bounds: 8.44424930131966E14\n"
                + "    explicit_bounds: 9.38249922368851E14\n" + "    explicit_bounds: 1.032074914605736E15\n"
                + "    explicit_bounds: 1.125899906842624E15\n" + "    explicit_bounds: 1.501199875790165E15\n"
                + "    explicit_bounds: 1.876499844737706E15\n" + "    explicit_bounds: 2.251799813685247E15\n"
                + "    explicit_bounds: 2.627099782632788E15\n" + "    explicit_bounds: 3.002399751580329E15\n"
                + "    explicit_bounds: 3.37769972052787E15\n" + "    explicit_bounds: 3.752999689475411E15\n"
                + "    explicit_bounds: 4.128299658422952E15\n" + "    explicit_bounds: 4.503599627370496E15\n"
                + "    explicit_bounds: 6.004799503160661E15\n" + "    explicit_bounds: 7.505999378950826E15\n"
                + "    explicit_bounds: 9.007199254740991E15\n" + "    explicit_bounds: 1.0508399130531156E16\n"
                + "    explicit_bounds: 1.200959900632132E16\n" + "    explicit_bounds: 1.3510798882111486E16\n"
                + "    explicit_bounds: 1.5011998757901652E16\n" + "    explicit_bounds: 1.6513198633691816E16\n"
                + "    explicit_bounds: 1.8014398509481984E16\n" + "    explicit_bounds: 2.4019198012642644E16\n"
                + "    explicit_bounds: 3.0023997515803304E16\n" + "    explicit_bounds: 3.6028797018963968E16\n"
                + "    explicit_bounds: 4.2033596522124624E16\n" + "    explicit_bounds: 4.8038396025285288E16\n"
                + "    explicit_bounds: 5.4043195528445952E16\n" + "    explicit_bounds: 6.0047995031606608E16\n"
                + "    explicit_bounds: 6.6052794534767272E16\n" + "    explicit_bounds: 7.2057594037927936E16\n"
                + "    explicit_bounds: 9.6076792050570576E16\n" + "    explicit_bounds: 1.20095990063213232E17\n"
                + "    explicit_bounds: 1.44115188075855872E17\n" + "    explicit_bounds: 1.68134386088498528E17\n"
                + "    explicit_bounds: 1.92153584101141152E17\n" + "    explicit_bounds: 2.16172782113783808E17\n"
                + "    explicit_bounds: 2.40191980126426464E17\n" + "    explicit_bounds: 2.64211178139069088E17\n"
                + "    explicit_bounds: 2.8823037615171174E17\n" + "    explicit_bounds: 3.843071682022823E17\n"
                + "    explicit_bounds: 4.8038396025285293E17\n" + "    explicit_bounds: 5.7646075230342349E17\n"
                + "    explicit_bounds: 6.7253754435399411E17\n" + "    explicit_bounds: 7.6861433640456461E17\n"
                + "    explicit_bounds: 8.6469112845513523E17\n" + "    explicit_bounds: 9.6076792050570586E17\n"
                + "    explicit_bounds: 1.05684471255627635E18\n" + "    explicit_bounds: 1.15292150460684698E18\n"
                + "    explicit_bounds: 1.53722867280912922E18\n" + "    explicit_bounds: 1.92153584101141171E18\n"
                + "    explicit_bounds: 2.305843009213694E18\n" + "    explicit_bounds: 2.6901501774159764E18\n"
                + "    explicit_bounds: 3.0744573456182584E18\n" + "    explicit_bounds: 3.4587645138205409E18\n"
                + "    explicit_bounds: 3.8430716820228234E18\n" + "    explicit_bounds: 4.2273788502251054E18\n"
                + "  }\n" + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n";
        String[] expectedLines = expected.split("\n");
        String actual = writeToMetric(size).toString();
        String[] actualLines = actual.split("\n");
        assertThat(actualLines).hasSameSizeAs(expectedLines);
        for (int i = 0; i < actualLines.length; i++) {
            String actualLine = actualLines[i];
            String expectedLine = expectedLines[i];

            // Comparing with double values, not with their String representation is
            // required since Java 19 as it has changed String representation for double
            // slightly in some cases.
            // See https://jdk.java.net/19/release-notes#JDK-4511638
            if (actualLine.contains("explicit_bounds") && !actualLine.contains("Infinity")) {
                double actualValue = extractValue(actualLine);
                double expectedValue = extractValue(expectedLine);
                assertThat(actualValue).isEqualTo(expectedValue);
            }
            else {
                assertThat(actualLine).isEqualTo(expectedLine);
            }
        }
    }

    @Test
    void distributionSummaryWithPercentiles() {
        DistributionSummary size = DistributionSummary.builder("http.response.size")
            .baseUnit(BaseUnits.BYTES)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);

        assertThat(writeToMetric(size).toString())
            .isEqualTo("name: \"http.response.size\"\n" + "unit: \"bytes\"\n" + "summary {\n" + "  data_points {\n"
                    + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 60001000000\n" + "    count: 4\n"
                    + "    sum: 2552.0\n" + "    quantile_values {\n" + "      quantile: 0.5\n" + "      value: 200.0\n"
                    + "    }\n" + "    quantile_values {\n" + "      quantile: 0.9\n" + "      value: 200.0\n"
                    + "    }\n" + "    quantile_values {\n" + "      quantile: 0.99\n" + "      value: 200.0\n"
                    + "    }\n" + "  }\n" + "}\n");
    }

    private double extractValue(String line) {
        return Double.parseDouble(line.substring(line.lastIndexOf(' ')));
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer taskTimer = LongTaskTimer.builder("checkout.batch").register(registry);
        LongTaskTimer.Sample task1 = taskTimer.start();
        LongTaskTimer.Sample task2 = taskTimer.start();
        this.clock.add(otlpConfig().step().multipliedBy(3));

        assertThat(writeToMetric(taskTimer).toString())
            .isEqualTo("name: \"checkout.batch\"\n" + "unit: \"milliseconds\"\n" + "histogram {\n" + "  data_points {\n"
                    + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 180001000000\n" + "    count: 2\n"
                    + "    sum: 360000.0\n" + "  }\n"
                    + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n");

        task1.stop();
        task2.stop();
        this.clock.add(otlpConfig().step());

        // this is not right that count/sum reset, but it's the same thing we do with
        // prometheus
        assertThat(writeToMetric(taskTimer).toString())
            .isEqualTo("name: \"checkout.batch\"\n" + "unit: \"milliseconds\"\n" + "histogram {\n" + "  data_points {\n"
                    + "    start_time_unix_nano: 1000000\n" + "    time_unix_nano: 240001000000\n" + "    sum: 0.0\n"
                    + "  }\n" + "  aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE\n" + "}\n");
    }

    @Override
    void testMetricsStartAndEndTime() {
        Counter counter = Counter.builder("test_publish_time").register(registry);
        final long startTime = ((StartTimeAwareMeter) counter).getStartTimeNanos();
        Function<Meter, NumberDataPoint> getDataPoint = (meter) -> writeToMetric(meter).getSum().getDataPoints(0);
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(1000000L);
        clock.addSeconds(59);
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(59001000000L);
        clock.addSeconds(1);
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(60001000000L);
    }

    @Test
    void testExponentialHistogramWithTimer() {
        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        timer.record(Duration.ofMillis(100));
        timer.record(Duration.ofMillis(1000));

        Metric metric = writeToMetric(timer);
        assertThat(metric.getExponentialHistogram().getDataPointsCount()).isPositive();

        ExponentialHistogramDataPoint exponentialHistogramDataPoint = metric.getExponentialHistogram().getDataPoints(0);
        assertExponentialHistogram(metric, 2, 1100, 0.0, 0, 5);
        ExponentialHistogramDataPoint.Buckets buckets = exponentialHistogramDataPoint.getPositive();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCountsCount()).isEqualTo(107);
        assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(105);

        long previousEndTime = exponentialHistogramDataPoint.getTimeUnixNano();

        clock.add(exponentialHistogramOtlpConfig().step());
        timer.record(Duration.ofMillis(10000));

        metric = writeToMetric(timer);
        exponentialHistogramDataPoint = metric.getExponentialHistogram().getDataPoints(0);
        assertThat(exponentialHistogramDataPoint.getTimeUnixNano() - previousEndTime)
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 3, 11100, 0.0, 0, 4);

        buckets = exponentialHistogramDataPoint.getPositive();
        assertThat(buckets.getOffset()).isEqualTo(106);
        assertThat(buckets.getBucketCountsCount()).isEqualTo(107);
        assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(53)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(104);
    }

    @Test
    void testExponentialHistogramDs() {
        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        ds.record(100);
        ds.record(1000);

        Metric metric = writeToMetric(ds);
        assertThat(metric.getExponentialHistogram().getDataPointsCount()).isPositive();

        ExponentialHistogramDataPoint exponentialHistogramDataPoint = metric.getExponentialHistogram().getDataPoints(0);
        assertExponentialHistogram(metric, 2, 1100, 0.0, 0, 5);
        ExponentialHistogramDataPoint.Buckets buckets = exponentialHistogramDataPoint.getPositive();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCountsCount()).isEqualTo(107);
        assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(105);

        long previousEndTime = exponentialHistogramDataPoint.getTimeUnixNano();

        clock.add(exponentialHistogramOtlpConfig().step());
        ds.record(10000);

        metric = writeToMetric(ds);
        exponentialHistogramDataPoint = metric.getExponentialHistogram().getDataPoints(0);
        assertThat(exponentialHistogramDataPoint.getTimeUnixNano() - previousEndTime)
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 3, 11100, 0.0, 0, 4);

        buckets = exponentialHistogramDataPoint.getPositive();
        assertThat(buckets.getOffset()).isEqualTo(106);
        assertThat(buckets.getBucketCountsCount()).isEqualTo(107);
        assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(53)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(104);
    }

}
