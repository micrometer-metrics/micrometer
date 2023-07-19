/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.newrelic;

import com.newrelic.api.agent.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.config.validate.ValidationException;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.newrelic.NewRelicMeterRegistryTest.MockNewRelicAgent.MockNewRelicInsights;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link NewRelicMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Neil Powell
 */
class NewRelicMeterRegistryTest {

    private final NewRelicConfig insightsAgentConfig = new NewRelicConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public ClientProviderType clientProviderType() {
            return ClientProviderType.INSIGHTS_AGENT;
        }

    };

    private final NewRelicConfig insightsApiConfig = new NewRelicConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String accountId() {
            return "accountId";
        }

        @Override
        public String apiKey() {
            return "apiKey";
        }

        @Override
        public ClientProviderType clientProviderType() {
            return ClientProviderType.INSIGHTS_API;
        }
    };

    private final NewRelicConfig meterNameEventTypeEnabledConfig = new NewRelicConfig() {

        @Override
        public boolean meterNameEventTypeEnabled() {
            // Previous behavior for backward compatibility
            return true;
        }

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String accountId() {
            return "accountId";
        }

        @Override
        public String apiKey() {
            return "apiKey";
        }

        @Override
        public ClientProviderType clientProviderType() {
            return ClientProviderType.INSIGHTS_API;
        }
    };

    private final MockClock clock = new MockClock();

    private final NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsApiConfig,
            mock(NewRelicClientProvider.class), clock);

    private final NewRelicMeterRegistry apiDefaultRegistry = new NewRelicMeterRegistry(insightsApiConfig, clock);

    private final NewRelicMeterRegistry agentEnabledRegistry = new NewRelicMeterRegistry(insightsAgentConfig, clock);

    NewRelicInsightsAgentClientProvider getInsightsAgentClientProvider(NewRelicConfig config) {
        return new NewRelicInsightsAgentClientProvider(config);
    }

    NewRelicInsightsApiClientProvider getInsightsApiClientProvider(NewRelicConfig config) {
        return new NewRelicInsightsApiClientProvider(config);
    }

    @Test
    void constructedWithAgentClientProvider() {
        // test Agent clientProvider
        assertThat(agentEnabledRegistry.clientProvider).isInstanceOf(NewRelicInsightsAgentClientProvider.class);
    }

    @Test
    void constructedWithApiClientProvider() {
        // test default API clientProvider
        assertThat(apiDefaultRegistry.clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);
    }

    @Test
    void writeGauge() {
        // test API clientProvider
        writeGauge(meterNameEventTypeEnabledConfig, "{\"eventType\":\"myGauge\",\"value\":1}");
        writeGauge(insightsApiConfig,
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        writeGauge(meterNameEventTypeEnabledConfig, expectedEntries);
        expectedEntries.put("metricName", "myGauge2");
        expectedEntries.put("metricType", "GAUGE");
        writeGauge(insightsAgentConfig, expectedEntries);
    }

    private void writeGauge(NewRelicConfig config, String expectedJson) {
        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).register(registry);
        assertThat(getInsightsApiClientProvider(config).writeGauge(gauge)).containsExactly(expectedJson);
    }

    private void writeGauge(NewRelicConfig config, Map<String, Object> expectedEntries) {
        Gauge gauge = Gauge.builder("my.gauge2", () -> 1d).register(registry);
        assertThat(getInsightsAgentClientProvider(config).writeGauge(gauge)).isEqualTo(expectedEntries);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        // test API clientProvider
        writeGaugeShouldDropNanValue(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropNanValue(getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeGaugeShouldDropNanValue(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropNanValue(getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeGaugeShouldDropNanValue(NewRelicInsightsApiClientProvider clientProvider) {
        Gauge gauge = Gauge.builder("my.gauge", () -> Double.NaN).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }

    private void writeGaugeShouldDropNanValue(NewRelicInsightsAgentClientProvider clientProvider) {
        Gauge gauge = Gauge.builder("my.gauge2", () -> Double.NaN).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        // test API clientProvider
        writeGaugeShouldDropInfiniteValues(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropInfiniteValues(getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeGaugeShouldDropInfiniteValues(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropInfiniteValues(getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeGaugeShouldDropInfiniteValues(NewRelicInsightsApiClientProvider clientProvider) {
        Gauge gauge = Gauge.builder("my.gauge", () -> Double.POSITIVE_INFINITY).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();

        gauge = Gauge.builder("my.gauge2", () -> Double.NEGATIVE_INFINITY).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }

    private void writeGaugeShouldDropInfiniteValues(NewRelicInsightsAgentClientProvider clientProvider) {
        Gauge gauge = Gauge.builder("my.gauge", () -> Double.POSITIVE_INFINITY).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();

        gauge = Gauge.builder("my.gauge2", () -> Double.NEGATIVE_INFINITY).register(registry);
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGauge() {
        // test API clientProvider
        writeGaugeWithTimeGauge(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myTimeGauge\",\"value\":1,\"timeUnit\":\"seconds\"}");
        writeGaugeWithTimeGauge(getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"timeUnit\":\"seconds\",\"metricName\":\"myTimeGauge\",\"metricType\":\"GAUGE\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        expectedEntries.put("timeUnit", "seconds");
        writeGaugeWithTimeGauge(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("metricName", "myTimeGauge2");
        expectedEntries.put("metricType", "GAUGE");
        writeGaugeWithTimeGauge(getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeGaugeWithTimeGauge(NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge", () -> 1d, TimeUnit.SECONDS).register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).containsExactly(expectedJson);
    }

    private void writeGaugeWithTimeGauge(NewRelicInsightsAgentClientProvider clientProvider,
            Map<String, Object> expectedEntries) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge2", () -> 1d, TimeUnit.SECONDS).register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEqualTo(expectedEntries);
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropNanValue() {
        // test API clientProvider
        writeGaugeWithTimeGaugeShouldDropNanValue(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropNanValue(getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeGaugeWithTimeGaugeShouldDropNanValue(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropNanValue(getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeGaugeWithTimeGaugeShouldDropNanValue(NewRelicInsightsApiClientProvider clientProvider) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge", () -> Double.NaN, TimeUnit.SECONDS).register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    private void writeGaugeWithTimeGaugeShouldDropNanValue(NewRelicInsightsAgentClientProvider clientProvider) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge2", () -> Double.NaN, TimeUnit.SECONDS).register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropInfiniteValues() {
        // test API clientProvider
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(
                getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeGaugeWithTimeGaugeShouldDropInfiniteValues(NewRelicInsightsApiClientProvider clientProvider) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge", () -> Double.POSITIVE_INFINITY, TimeUnit.SECONDS)
            .register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();

        timeGauge = TimeGauge.builder("my.timeGauge", () -> Double.NEGATIVE_INFINITY, TimeUnit.SECONDS)
            .register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    private void writeGaugeWithTimeGaugeShouldDropInfiniteValues(NewRelicInsightsAgentClientProvider clientProvider) {
        TimeGauge timeGauge = TimeGauge.builder("my.timeGauge2", () -> Double.POSITIVE_INFINITY, TimeUnit.SECONDS)
            .register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();

        timeGauge = TimeGauge.builder("my.timeGauge2", () -> Double.NEGATIVE_INFINITY, TimeUnit.SECONDS)
            .register(registry);
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeCounterWithFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(insightsApiConfig.step());
        // test API clientProvider
        writeCounterWithFunctionCounter(counter, getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myCounter\",\"throughput\":1}");
        writeCounterWithFunctionCounter(counter, getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"throughput\":1,\"metricName\":\"myCounter\",\"metricType\":\"COUNTER\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("throughput", 1);
        writeCounterWithFunctionCounter(counter, getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig),
                expectedEntries);
        expectedEntries.put("metricName", "myCounter");
        expectedEntries.put("metricType", "COUNTER");
        writeCounterWithFunctionCounter(counter, getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeCounterWithFunctionCounter(FunctionCounter counter,
            NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        assertThat(clientProvider.writeFunctionCounter(counter)).containsExactly(expectedJson);
    }

    private void writeCounterWithFunctionCounter(FunctionCounter counter,
            NewRelicInsightsAgentClientProvider clientProvider, Map<String, Object> expectedEntries) {
        assertThat(clientProvider.writeFunctionCounter(counter)).isEqualTo(expectedEntries);
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        // test API clientProvider
        writeCounterWithFunctionCounterShouldDropInfiniteValues(
                getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeCounterWithFunctionCounterShouldDropInfiniteValues(
                getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeCounterWithFunctionCounterShouldDropInfiniteValues(
            NewRelicInsightsApiClientProvider clientProvider) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
            .register(registry);
        clock.add(insightsApiConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
            .register(registry);
        clock.add(insightsApiConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();
    }

    private void writeCounterWithFunctionCounterShouldDropInfiniteValues(
            NewRelicInsightsAgentClientProvider clientProvider) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
            .register(registry);
        clock.add(insightsAgentConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
            .register(registry);
        clock.add(insightsAgentConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeTimer() {
        // test API clientProvider
        writeTimer(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myTimer\",\"count\":0,\"avg\":0,\"totalTime\":0,\"max\":0,\"timeUnit\":\"seconds\"}");
        writeTimer(getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"count\":0,\"avg\":0,\"totalTime\":0,\"max\":0,\"timeUnit\":\"seconds\",\"metricName\":\"myTimer\",\"metricType\":\"TIMER\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("count", 0);
        expectedEntries.put("avg", 0);
        expectedEntries.put("totalTime", 0);
        expectedEntries.put("max", 0);
        expectedEntries.put("timeUnit", "seconds");
        writeTimer(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("count", 0);
        expectedEntries.put("avg", 0);
        expectedEntries.put("totalTime", 0);
        expectedEntries.put("max", 0);
        expectedEntries.put("metricName", "myTimer2");
        expectedEntries.put("metricType", "TIMER");
        writeTimer(getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeTimer(NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        registry.timer("my.timer", Tags.empty());
        Timer timer = registry.get("my.timer").timer();
        assertThat(clientProvider.writeTimer(timer)).containsExactly(expectedJson);
    }

    private void writeTimer(NewRelicInsightsAgentClientProvider clientProvider, Map<String, Object> expectedEntries) {
        registry.timer("my.timer2", Tags.empty());
        Timer timer = registry.get("my.timer2").timer();
        assertThat(clientProvider.writeTimer(timer)).isEqualTo(expectedEntries);
    }

    @Test
    void writeFunctionTimer() {
        // test API clientProvider
        writeFunctionTimer(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myFunTimer\",\"count\":0,\"avg\":0,\"totalTime\":0,\"timeUnit\":\"seconds\"}");
        writeFunctionTimer(getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"count\":0,\"avg\":0,\"totalTime\":0,\"timeUnit\":\"seconds\",\"metricName\":\"myFunTimer\",\"metricType\":\"TIMER\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("avg", 0);
        expectedEntries.put("count", 0);
        expectedEntries.put("timeUnit", "seconds");
        expectedEntries.put("totalTime", 0);
        writeFunctionTimer(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("avg", 0);
        expectedEntries.put("count", 0);
        expectedEntries.put("timeUnit", "seconds");
        expectedEntries.put("totalTime", 0);
        expectedEntries.put("metricName", "myFunTimer2");
        expectedEntries.put("metricType", "TIMER");
        writeFunctionTimer(getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeFunctionTimer(NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        Object o = new Object();
        registry.more().timer("myFunTimer", emptyList(), o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer functionTimer = registry.get("myFunTimer").functionTimer();
        assertThat(clientProvider.writeFunctionTimer(functionTimer)).containsExactly(expectedJson);
    }

    private void writeFunctionTimer(NewRelicInsightsAgentClientProvider clientProvider,
            Map<String, Object> expectedEntries) {
        Object o = new Object();
        registry.more().timer("myFunTimer2", emptyList(), o, o2 -> 1, o2 -> 1, TimeUnit.MILLISECONDS);

        FunctionTimer functionTimer = registry.get("myFunTimer2").functionTimer();
        assertThat(clientProvider.writeFunctionTimer(functionTimer)).isEqualTo(expectedEntries);
    }

    @Test
    void writeLongTaskTimer() {
        // test API clientProvider
        writeLongTaskTimer(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myLongTaskTimer\",\"activeTasks\":0,\"duration\":0,\"timeUnit\":\"seconds\"}");
        writeLongTaskTimer(getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"activeTasks\":0,\"duration\":0,\"timeUnit\":\"seconds\",\"metricName\":\"myLongTaskTimer\",\"metricType\":\"LONG_TASK_TIMER\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("activeTasks", 0);
        expectedEntries.put("duration", 0);
        expectedEntries.put("timeUnit", "seconds");
        writeLongTaskTimer(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("activeTasks", 0);
        expectedEntries.put("duration", 0);
        expectedEntries.put("timeUnit", "seconds");
        expectedEntries.put("metricName", "myLongTaskTimer2");
        expectedEntries.put("metricType", "LONG_TASK_TIMER");
        writeLongTaskTimer(getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeLongTaskTimer(NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        registry.more().longTaskTimer("myLongTaskTimer", emptyList());
        LongTaskTimer longTaskTimer = registry.get("myLongTaskTimer").longTaskTimer();
        assertThat(clientProvider.writeLongTaskTimer(longTaskTimer)).containsExactly(expectedJson);
    }

    private void writeLongTaskTimer(NewRelicInsightsAgentClientProvider clientProvider,
            Map<String, Object> expectedEntries) {
        registry.more().longTaskTimer("myLongTaskTimer2", emptyList());
        LongTaskTimer longTaskTimer = registry.get("myLongTaskTimer2").longTaskTimer();
        assertThat(clientProvider.writeLongTaskTimer(longTaskTimer)).isEqualTo(expectedEntries);
    }

    @Test
    void writeSummary() {
        // test API clientProvider
        writeSummary(getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myDistSummary\",\"count\":0,\"avg\":0,\"total\":0,\"max\":0}");
        writeSummary(getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"count\":0,\"avg\":0,\"total\":0,\"max\":0,\"metricName\":\"myDistSummary\",\"metricType\":\"DISTRIBUTION_SUMMARY\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("avg", 0);
        expectedEntries.put("count", 0);
        expectedEntries.put("max", 0);
        expectedEntries.put("total", 0);
        writeSummary(getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("avg", 0);
        expectedEntries.put("count", 0);
        expectedEntries.put("max", 0);
        expectedEntries.put("total", 0);
        expectedEntries.put("metricName", "myDistSummary2");
        expectedEntries.put("metricType", "DISTRIBUTION_SUMMARY");
        writeSummary(getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeSummary(NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        registry.summary("myDistSummary", emptyList());
        DistributionSummary summary = registry.get("myDistSummary").summary();
        assertThat(clientProvider.writeSummary(summary)).containsExactly(expectedJson);
    }

    private void writeSummary(NewRelicInsightsAgentClientProvider clientProvider, Map<String, Object> expectedEntries) {
        registry.summary("myDistSummary2", emptyList());
        DistributionSummary summary = registry.get("myDistSummary2").summary();
        assertThat(clientProvider.writeSummary(summary)).isEqualTo(expectedEntries);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);

        // test API clientProvider
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(measurements,
                getInsightsApiClientProvider(meterNameEventTypeEnabledConfig));
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(measurements,
                getInsightsApiClientProvider(insightsApiConfig));

        // test Agent clientProvider
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(measurements,
                getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(measurements,
                getInsightsAgentClientProvider(insightsAgentConfig));
    }

    private void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(List<Measurement> measurements,
            NewRelicInsightsApiClientProvider clientProvider) {
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).isEmpty();
    }

    private void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(List<Measurement> measurements,
            NewRelicInsightsAgentClientProvider clientProvider) {
        Meter meter = Meter.builder("my.meter2", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4);
        // test API clientProvider
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(measurements,
                getInsightsApiClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myMeter\",\"value\":1}");
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(measurements,
                getInsightsApiClientProvider(insightsApiConfig),
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(measurements,
                getInsightsAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("metricName", "myMeter2");
        expectedEntries.put("metricType", "GAUGE");
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(measurements,
                getInsightsAgentClientProvider(insightsAgentConfig), expectedEntries);
    }

    private void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
            List<Measurement> measurements, NewRelicInsightsApiClientProvider clientProvider, String expectedJson) {
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).containsExactly(expectedJson);
    }

    private void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
            List<Measurement> measurements, NewRelicInsightsAgentClientProvider clientProvider,
            Map<String, Object> expectedEntries) {
        Meter meter = Meter.builder("my.meter2", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).isEqualTo(expectedEntries);
    }

    @Test
    void writeMeterWhenCustomMeterHasDuplicatesKeysShouldWriteOnlyLastValue() {
        Measurement measurement1 = new Measurement(() -> 3d, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        // test API clientProvider
        assertThat(getInsightsApiClientProvider(insightsApiConfig).writeMeter(meter)).containsExactly(
                "{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");

        // test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 2);
        expectedEntries.put("metricName", "myMeter");
        expectedEntries.put("metricType", "GAUGE");
        assertThat(getInsightsAgentClientProvider(insightsAgentConfig).writeMeter(meter)).isEqualTo(expectedEntries);
    }

    @Test
    void sendEventsWithApiProvider() {
        // test meterNameEventTypeEnabledConfig = false (default)
        MockHttpSender mockHttpClient = new MockHttpSender();
        NewRelicInsightsApiClientProvider apiProvider = new NewRelicInsightsApiClientProvider(insightsApiConfig,
                mockHttpClient, registry.config().namingConvention());

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsApiConfig, apiProvider, clock);

        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).register(registry);

        apiProvider.sendEvents(apiProvider.writeGauge(gauge));

        assertThat(new String(mockHttpClient.getRequest().getEntity())).contains(
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");

        // test meterNameEventTypeEnabledConfig = true
        mockHttpClient = new MockHttpSender();
        apiProvider = new NewRelicInsightsApiClientProvider(meterNameEventTypeEnabledConfig, mockHttpClient,
                registry.config().namingConvention());

        gauge = Gauge.builder("my.gauge2", () -> 1d).register(registry);

        apiProvider.sendEvents(apiProvider.writeGauge(gauge));

        assertThat(new String(mockHttpClient.getRequest().getEntity()))
            .contains("{\"eventType\":\"myGauge2\",\"value\":1}");
    }

    @Test
    void sendEventsWithAgentProvider() {
        // test meterNameEventTypeEnabledConfig = false (default)
        MockNewRelicAgent mockNewRelicAgent = new MockNewRelicAgent();
        NewRelicInsightsAgentClientProvider agentProvider = new NewRelicInsightsAgentClientProvider(insightsAgentConfig,
                mockNewRelicAgent, registry.config().namingConvention());

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsAgentConfig, agentProvider, clock);

        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).register(registry);

        agentProvider.sendEvents(gauge.getId(), agentProvider.writeGauge(gauge));

        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getEventType())
            .isEqualTo("MicrometerSample");
        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getAttributes())
            .hasSize(3);

        // test meterNameEventTypeEnabledConfig = true
        mockNewRelicAgent = new MockNewRelicAgent();
        agentProvider = new NewRelicInsightsAgentClientProvider(meterNameEventTypeEnabledConfig, mockNewRelicAgent,
                registry.config().namingConvention());

        gauge = Gauge.builder("my.gauge2", () -> 1d).register(registry);

        agentProvider.sendEvents(gauge.getId(), agentProvider.writeGauge(gauge));

        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getEventType())
            .isEqualTo("myGauge2");
        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getAttributes())
            .hasSize(1);
    }

    @Test
    void publishWithApiClientProvider() {
        // test meterNameEventTypeEnabledConfig = false (default)
        MockHttpSender mockHttpClient = new MockHttpSender();
        NewRelicInsightsApiClientProvider apiProvider = new NewRelicInsightsApiClientProvider(insightsApiConfig,
                mockHttpClient, registry.config().namingConvention());

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsApiConfig, apiProvider, clock);

        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).tag("theTag", "theValue").register(registry);
        assertThat(gauge).isNotNull();

        Gauge other = Gauge.builder("other.gauge", () -> 2d).register(registry);
        assertThat(other).isNotNull();

        registry.publish();

        // should send a batch of multiple in one json payload
        assertThat(new String(mockHttpClient.getRequest().getEntity())).contains(
                "[{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"otherGauge\",\"metricType\":\"GAUGE\"},"
                        + "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\",\"theTag\":\"theValue\"}]");
    }

    @Test
    void publishWithAgentClientProvider() {
        // test meterNameEventTypeEnabledConfig = false (default)
        MockNewRelicAgent mockNewRelicAgent = new MockNewRelicAgent();
        NewRelicInsightsAgentClientProvider agentProvider = new NewRelicInsightsAgentClientProvider(insightsAgentConfig,
                mockNewRelicAgent, registry.config().namingConvention());

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsAgentConfig, agentProvider, clock);

        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).tags("theTag", "theValue").register(registry);
        assertThat(gauge).isNotNull();

        Gauge other = Gauge.builder("other.gauge", () -> 2d).register(registry);
        assertThat(other).isNotNull();

        registry.publish();

        // should delegate to the Agent one at a time
        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getEventType())
            .isEqualTo("MicrometerSample");
        assertThat(((MockNewRelicInsights) mockNewRelicAgent.getInsights()).getInsightData().getAttributes())
            .hasSize(4);
    }

    @Test
    void succeedsCustomClientProvider() {
        NewRelicConfig config = key -> null;
        NewRelicClientProvider customClientProvider = mock(NewRelicClientProvider.class);
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(config, customClientProvider, clock);

        assertThat(registry.clientProvider).isSameAs(customClientProvider);
    }

    @Test
    void succeedsConfigInsightsApiClientProviderAndDefaultNamingConvention() {
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsApiConfig, null, clock);

        assertThat(registry.clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);

        assertThat(((NewRelicInsightsApiClientProvider) registry.clientProvider).namingConvention)
            .isInstanceOf(NewRelicNamingConvention.class);
        assertThat(registry.config().namingConvention()).isInstanceOf(NewRelicNamingConvention.class);
    }

    @Test
    void succeedsConfigInsightsApiClientProviderAndCustomNamingConvention() {
        NamingConvention customNamingConvention = mock(NewRelicNamingConvention.class);

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsApiConfig, null, customNamingConvention,
                clock, new NamedThreadFactory("new-relic-test"));

        assertThat(registry.clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);

        assertThat(((NewRelicInsightsApiClientProvider) registry.clientProvider).namingConvention)
            .isSameAs(customNamingConvention);
        assertThat(registry.config().namingConvention()).isSameAs(customNamingConvention);
    }

    @Test
    void succeedsConfigInsightsAgentClientProviderAndDefaultNamingConvention() {
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsAgentConfig, null, clock);

        assertThat(registry.clientProvider).isInstanceOf(NewRelicInsightsAgentClientProvider.class);

        assertThat(((NewRelicInsightsAgentClientProvider) registry.clientProvider).namingConvention)
            .isInstanceOf(NewRelicNamingConvention.class);
        assertThat(registry.config().namingConvention()).isInstanceOf(NewRelicNamingConvention.class);
    }

    @Test
    void succeedsConfigInsightsAgentClientProviderAndCustomNamingConvention() {
        NamingConvention customNamingConvention = mock(NewRelicNamingConvention.class);

        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(insightsAgentConfig, null, customNamingConvention,
                clock, new NamedThreadFactory("new-relic-test"));

        assertThat(registry.clientProvider).isInstanceOf(NewRelicInsightsAgentClientProvider.class);

        assertThat(((NewRelicInsightsAgentClientProvider) registry.clientProvider).namingConvention)
            .isSameAs(customNamingConvention);
        assertThat(registry.config().namingConvention()).isSameAs(customNamingConvention);
    }

    @Test
    void failsConfigApiMissingEventType() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String eventType() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThatThrownBy(() -> getInsightsApiClientProvider(config)).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("eventType");
    }

    @Test
    void succeedsConfigApiMissingEventType() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public boolean meterNameEventTypeEnabled() {
                return true;
            }

            @Override
            public String eventType() {
                return "";
            }

            @Override
            public String accountId() {
                return "accountId";
            }

            @Override
            public String apiKey() {
                return "apiKey";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThat(getInsightsApiClientProvider(config)).isNotNull();
    }

    @Test
    void failsConfigApiMissingAccountId() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String eventType() {
                return "eventType";
            }

            @Override
            public String accountId() {
                return null;
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThatThrownBy(() -> getInsightsApiClientProvider(config)).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("accountId");
    }

    @Test
    void failsConfigApiMissingApiKey() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String eventType() {
                return "eventType";
            }

            @Override
            public String accountId() {
                return "accountId";
            }

            @Override
            public String apiKey() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThatThrownBy(() -> getInsightsApiClientProvider(config)).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("apiKey");
    }

    @Test
    void failsConfigApiMissingUri() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String eventType() {
                return "eventType";
            }

            @Override
            public String accountId() {
                return "accountId";
            }

            @Override
            public String apiKey() {
                return "apiKey";
            }

            @Override
            public String uri() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThatThrownBy(() -> getInsightsApiClientProvider(config)).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("uri");
    }

    @Test
    void failsConfigAgentMissingEventType() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String eventType() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThatThrownBy(() -> getInsightsAgentClientProvider(config)).isExactlyInstanceOf(ValidationException.class)
            .hasMessageContaining("eventType");
    }

    @Test
    void succeedsConfigAgentMissingEventType() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public boolean meterNameEventTypeEnabled() {
                return true;
            }

            @SuppressWarnings("ConstantConditions")
            @Override
            public String eventType() {
                return null;
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public ClientProviderType clientProviderType() {
                return ClientProviderType.INSIGHTS_AGENT;
            }
        };

        assertThat(getInsightsAgentClientProvider(config)).isNotNull();
    }

    @SuppressWarnings("deprecation")
    @Test
    void canCustomizeHttpSenderViaBuilder_deprecated() {
        HttpSender httpSender = mock(HttpSender.class);
        NewRelicClientProvider clientProvider = NewRelicMeterRegistry.builder(insightsApiConfig)
            .httpClient(httpSender)
            .build().clientProvider;
        assertThat(clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);
        assertThat(((NewRelicInsightsApiClientProvider) clientProvider).httpClient).isEqualTo(httpSender);
    }

    @Test
    void canCustomizeHttpSenderViaBuilder() {
        HttpSender httpSender = mock(HttpSender.class);
        NewRelicClientProvider clientProvider = NewRelicMeterRegistry.builder(insightsApiConfig)
            .clientProvider(new NewRelicInsightsApiClientProvider(insightsApiConfig, httpSender,
                    new NewRelicNamingConvention()))
            .build().clientProvider;
        assertThat(clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);
        assertThat(((NewRelicInsightsApiClientProvider) clientProvider).httpClient).isEqualTo(httpSender);
    }

    @Test
    void canChangeNamingConventionThroughConfig() {
        NamingConvention namingConvention1 = mock(NamingConvention.class);
        NamingConvention namingConvention2 = mock(NamingConvention.class);
        NewRelicMeterRegistry meterRegistry = NewRelicMeterRegistry.builder(insightsApiConfig)
            .namingConvention(namingConvention1)
            .build();

        assertThat(meterRegistry.config().namingConvention()).isEqualTo(namingConvention1);
        assertThat(meterRegistry.clientProvider).isInstanceOf(NewRelicInsightsApiClientProvider.class);
        assertThat(((NewRelicInsightsApiClientProvider) meterRegistry.clientProvider).namingConvention)
            .isEqualTo(namingConvention1);

        meterRegistry.config().namingConvention(namingConvention2);
        assertThat(meterRegistry.config().namingConvention()).isEqualTo(namingConvention2);
        assertThat(((NewRelicInsightsApiClientProvider) meterRegistry.clientProvider).namingConvention)
            .isEqualTo(namingConvention2);
    }

    @SuppressWarnings("deprecation")
    @Test
    void builderBuildWhenBothHttpClientAndClientProviderAreSetShouldThrowIllegalStateException() {
        NewRelicConfig config = key -> null;
        assertThatIllegalStateException()
            .isThrownBy(() -> new NewRelicMeterRegistry.Builder(config).httpClient(mock(HttpSender.class))
                .clientProvider(mock(NewRelicClientProvider.class))
                .build());
    }

    static class MockHttpSender implements HttpSender {

        private Request request;

        @Override
        public Response send(Request request) {
            this.request = request;
            return new Response(200, "body");
        }

        public Request getRequest() {
            return request;
        }

    }

    static class MockNewRelicAgent implements Agent {

        private final Insights insights;

        public MockNewRelicAgent() {
            this.insights = new MockNewRelicInsights();
        }

        @Override
        public Config getConfig() {
            // No-op
            return null;
        }

        @Override
        public Insights getInsights() {
            return insights;
        }

        @Override
        public Logger getLogger() {
            // No-op
            return null;
        }

        @Override
        public MetricAggregator getMetricAggregator() {
            // No-op
            return null;
        }

        @Override
        public TracedMethod getTracedMethod() {
            // No-op
            return null;
        }

        @Override
        public Transaction getTransaction() {
            // No-op
            return null;
        }

        @Override
        public Map<String, String> getLinkingMetadata() {
            // No-op
            return null;
        }

        @Override
        public TraceMetadata getTraceMetadata() {
            // No-op
            return null;
        }

        static class MockNewRelicInsights implements Insights {

            private InsightData insightData;

            public InsightData getInsightData() {
                return insightData;
            }

            @Override
            public void recordCustomEvent(String eventType, Map<String, ?> attributes) {
                this.insightData = new InsightData(eventType, attributes);
            }

            static class InsightData {

                private String eventType;

                private Map<String, ?> attributes;

                public InsightData(String eventType, Map<String, ?> attributes) {
                    this.eventType = eventType;
                    this.attributes = attributes;
                }

                public String getEventType() {
                    return eventType;
                }

                public Map<String, ?> getAttributes() {
                    return attributes;
                }

            }

        }

    }

}
