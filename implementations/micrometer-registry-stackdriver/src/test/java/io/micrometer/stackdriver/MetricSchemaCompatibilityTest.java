/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.stackdriver;

import com.google.api.MetricDescriptor;
import com.google.monitoring.v3.TimeSeries;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.api.MetricDescriptor.MetricKind.CUMULATIVE;
import static com.google.api.MetricDescriptor.MetricKind.GAUGE;
import static com.google.api.MetricDescriptor.ValueType.*;
import static org.assertj.core.api.Assertions.assertThat;

class MetricSchemaCompatibilityTest {

    private final Map<String, String> config = new HashMap<>(
            Collections.singletonMap("stackdriver.projectId", "projectId"));

    private final StackdriverMeterRegistry registry = new StackdriverMeterRegistry(config::get, new MockClock());

    private final StackdriverMeterRegistry.Batch batch = registry.new Batch();

    /**
     * Assuring that when the configuration flag useSemanticMetricTypes is NOT set,
     * created metric types match the old behavior.
     */
    @Test
    void backwardCompatibleMetricTypes() {
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)),
                registry.createGauge(batch, Gauge.builder("gauge", () -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)),
                registry.createCounter(batch, Counter.builder("counter").register(registry)));
        assertSchemaCompatibility(
                Arrays.asList(new Pair(GAUGE, DISTRIBUTION), new Pair(GAUGE, DOUBLE), new Pair(GAUGE, INT64)),
                registry.createTimer(batch, Timer.builder("timer").register(registry)));
        assertSchemaCompatibility(
                Arrays.asList(new Pair(GAUGE, DISTRIBUTION), new Pair(GAUGE, DOUBLE), new Pair(GAUGE, INT64)),
                registry.createSummary(batch, DistributionSummary.builder("summary").register(registry)));
        assertSchemaCompatibility(Arrays.asList(new Pair(GAUGE, INT64), new Pair(GAUGE, DOUBLE)),
                registry.createLongTaskTimer(batch, LongTaskTimer.builder("longTaskTimer").register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)), registry.createTimeGauge(batch,
                TimeGauge.builder("timeGauge", () -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)), registry.createFunctionCounter(
                batch, FunctionCounter.builder("functionCounter", 1, value -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DISTRIBUTION)),
                registry.createFunctionTimer(batch,
                        FunctionTimer.builder("functionTimer", 1, value -> 1, value -> 1, TimeUnit.SECONDS)
                            .register(registry)));
        assertSchemaCompatibility(
                Collections.singletonList(new Pair(GAUGE,
                        DOUBLE)),
                registry.createMeter(batch,
                        Meter
                            .builder("gauge", Meter.Type.OTHER,
                                    Collections.singletonList(new Measurement(() -> 1.0, Statistic.UNKNOWN)))
                            .register(registry)));
    }

    /**
     * Assuring that when the configuration flag useSemanticMetricTypes is set, metric
     * types matching their meaning are used.
     */
    @Test
    void semanticMetricTypes() {
        config.put("stackdriver.useSemanticMetricTypes", "true");

        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)),
                registry.createGauge(batch, Gauge.builder("gauge", () -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(CUMULATIVE, DOUBLE)),
                registry.createCounter(batch, Counter.builder("counter").register(registry)));
        assertSchemaCompatibility(
                Arrays.asList(new Pair(GAUGE, DISTRIBUTION), new Pair(GAUGE, DOUBLE), new Pair(CUMULATIVE, INT64)),
                registry.createTimer(batch, Timer.builder("timer").register(registry)));
        assertSchemaCompatibility(
                Arrays.asList(new Pair(GAUGE, DISTRIBUTION), new Pair(GAUGE, DOUBLE), new Pair(CUMULATIVE, INT64)),
                registry.createSummary(batch, DistributionSummary.builder("summary").register(registry)));
        assertSchemaCompatibility(Arrays.asList(new Pair(GAUGE, INT64), new Pair(GAUGE, DOUBLE)),
                registry.createLongTaskTimer(batch, LongTaskTimer.builder("longTaskTimer").register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DOUBLE)), registry.createTimeGauge(batch,
                TimeGauge.builder("timeGauge", () -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(CUMULATIVE, DOUBLE)),
                registry.createFunctionCounter(batch,
                        FunctionCounter.builder("functionCounter", 1, value -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(new Pair(GAUGE, DISTRIBUTION)),
                registry.createFunctionTimer(batch,
                        FunctionTimer.builder("functionTimer", 1, value -> 1, value -> 1, TimeUnit.SECONDS)
                            .register(registry)));
        assertSchemaCompatibility(
                Collections.singletonList(new Pair(GAUGE,
                        DOUBLE)),
                registry.createMeter(batch,
                        Meter
                            .builder("gauge", Meter.Type.OTHER,
                                    Collections.singletonList(new Measurement(() -> 1.0, Statistic.UNKNOWN)))
                            .register(registry)));
    }

    private void assertSchemaCompatibility(List<Pair> expectedValues, Stream<TimeSeries> timeSeriesStream) {
        List<TimeSeries> timeSeries = timeSeriesStream.collect(Collectors.toList());
        assertThat(timeSeries.size()).isEqualTo(expectedValues.size());
        for (int i = 0; i < expectedValues.size(); i++) {
            assertThat(timeSeries.get(i).getMetricKind()).isEqualTo(expectedValues.get(i).metricKind);
            assertThat(timeSeries.get(i).getValueType()).isEqualTo(expectedValues.get(i).valueType);
        }
    }

    private static class Pair {

        private Pair(MetricDescriptor.MetricKind metricKind, MetricDescriptor.ValueType valueType) {
            this.metricKind = metricKind;
            this.valueType = valueType;
        }

        private final MetricDescriptor.MetricKind metricKind;

        private final MetricDescriptor.ValueType valueType;

    }

}
