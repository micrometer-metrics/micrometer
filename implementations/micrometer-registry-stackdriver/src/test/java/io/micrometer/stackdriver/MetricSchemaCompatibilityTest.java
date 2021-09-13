package io.micrometer.stackdriver;

import static com.google.api.MetricDescriptor.MetricKind.*;
import static com.google.api.MetricDescriptor.ValueType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.api.MetricDescriptor;
import com.google.common.collect.ImmutableMap;
import com.google.monitoring.v3.TimeSeries;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;

public class MetricSchemaCompatibilityTest {

    private final Map<String, String> config = new HashMap<String, String>() {{
        put("stackdriver.projectId", "projectId");
    }};
    private final StackdriverMeterRegistry registry = new StackdriverMeterRegistry(config::get, new MockClock());
    private final StackdriverMeterRegistry.Batch batch = registry.new Batch();

    /**
     * Assuring that when the configuration flag semanticMetricTypes is NOT set,
     * created metric types match the old behavior.
     */
    @Test
    void backwardCompatibleMetricTypes() {
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createGauge(batch, Gauge.builder("gauge", () -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createCounter(batch, Counter.builder("counter").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, DISTRIBUTION),
                        new Pair(GAUGE, DOUBLE),
                        new Pair(GAUGE, INT64)),
                registry.createTimer(batch, Timer.builder("timer").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, DISTRIBUTION),
                        new Pair(GAUGE, DOUBLE),
                        new Pair(GAUGE, INT64)),
                registry.createSummary(batch, DistributionSummary.builder("summary").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, INT64),
                        new Pair(GAUGE, DOUBLE)),
                registry.createLongTaskTimer(batch, LongTaskTimer.builder("longTaskTimer").register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createTimeGauge(batch, TimeGauge.builder("timeGauge", () -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createFunctionCounter(batch, FunctionCounter.builder("functionCounter", 1, value -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DISTRIBUTION)),
                registry.createFunctionTimer(batch, FunctionTimer.builder("functionTimer", 1, value -> 1, value -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createMeter(batch, Meter.builder("gauge", Meter.Type.OTHER, Collections.singletonList(new Measurement(() -> 1.0, Statistic.UNKNOWN))).register(registry)));
    }

    /**
     * Assuring that when the configuration flag semanticMetricTypes is set,
     * metric types matching their meaning are used.
     */
    @Test
    void semanticMetricTypes() {
        config.put("stackdriver.semanticMetricTypes", "true");

        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createGauge(batch, Gauge.builder("gauge", () -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(CUMULATIVE, DOUBLE)),
                registry.createCounter(batch, Counter.builder("counter").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, DISTRIBUTION),
                        new Pair(GAUGE, DOUBLE),
                        new Pair(CUMULATIVE, INT64)),
                registry.createTimer(batch, Timer.builder("timer").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, DISTRIBUTION),
                        new Pair(GAUGE, DOUBLE),
                        new Pair(CUMULATIVE, INT64)),
                registry.createSummary(batch, DistributionSummary.builder("summary").register(registry)));
        assertSchemaCompatibility(Arrays.asList(
                        new Pair(GAUGE, INT64),
                        new Pair(GAUGE, DOUBLE)),
                registry.createLongTaskTimer(batch, LongTaskTimer.builder("longTaskTimer").register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createTimeGauge(batch, TimeGauge.builder("timeGauge", () -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(CUMULATIVE, DOUBLE)),
                registry.createFunctionCounter(batch, FunctionCounter.builder("functionCounter", 1, value -> 1).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DISTRIBUTION)),
                registry.createFunctionTimer(batch, FunctionTimer.builder("functionTimer", 1, value -> 1, value -> 1, TimeUnit.SECONDS).register(registry)));
        assertSchemaCompatibility(Collections.singletonList(
                        new Pair(GAUGE, DOUBLE)),
                registry.createMeter(batch, Meter.builder("gauge", Meter.Type.OTHER, Collections.singletonList(new Measurement(() -> 1.0, Statistic.UNKNOWN))).register(registry)));
    }

    private void assertSchemaCompatibility(List<Pair> expectedValues, Stream<TimeSeries> timeSeriesStream) {
        List<TimeSeries> timeSeries = timeSeriesStream.collect(Collectors.toList());
        assertEquals(expectedValues.size(), timeSeries.size());
        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(expectedValues.get(i).metricKind, timeSeries.get(i).getMetricKind());
            assertEquals(expectedValues.get(i).valueType, timeSeries.get(i).getValueType());
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
