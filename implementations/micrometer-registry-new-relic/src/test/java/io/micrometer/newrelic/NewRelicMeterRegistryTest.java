/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.newrelic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.newrelic.api.agent.Insights;
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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link NewRelicMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Galen Schmidt
 */
@SuppressWarnings("ConstantConditions")
class NewRelicMeterRegistryTest {

    private final NewRelicConfig meterNameEventTypeDisabledConfig = new MockNewRelicConfig() {

        @Override
        public boolean meterNameEventTypeEnabled() {
            return false; // note: false is the default value
        }

    };

    private final NewRelicConfig meterNameEventTypeEnabledConfig = new MockNewRelicConfig() {

        @Override
        public boolean meterNameEventTypeEnabled() {
            return true;
        }

    };

    private final Insights insights = Mockito.mock(Insights.class, RETURNS_DEFAULTS);

    private final HttpSender httpSender = mock(HttpSender.class, CALLS_REAL_METHODS);

    private final NamedThreadFactory threadFactory = new NamedThreadFactory(getClass().getSimpleName());

    private final ObjectMapper mapper = new ObjectMapper();

    private final MockClock clock = new MockClock();

    private final NewRelicMeterRegistry meterNameEventTypeEnabledRegistry = new NewRelicMeterRegistry(meterNameEventTypeEnabledConfig, clock);

    private final NewRelicMeterRegistry meterNameEventTypeDisabledRegistry = new NewRelicMeterRegistry(meterNameEventTypeDisabledConfig, clock);

    @Test
    void eventFor_Gauge() {
        eventFor_Gauge(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myGauge")
                .put("value", 1)
                .build()
        );

        eventFor_Gauge(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Gauge(NewRelicMeterRegistry meterRegistry, Map<String, Object> expected) {
        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).register(meterRegistry);

        assertThat(toMap(meterRegistry.eventFor(gauge))).isEqualTo(expected);
    }

    @Test
    void eventFor_Gauge_withRegistryLevelTags() {
        eventFor_Gauge_withRegistryLevelTags(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myGauge")
                .put("registry_tag_key", "registry_tag_value")
                .put("value", 1)
                .build()
        );

        eventFor_Gauge_withRegistryLevelTags(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("registry_tag_key", "registry_tag_value")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Gauge_withRegistryLevelTags(NewRelicMeterRegistry meterRegistry, Map<String, Object> expected) {
        meterRegistry.config().commonTags("registry_tag_key", "registry_tag_value");
        Gauge gauge = Gauge.builder("my.gauge", () -> 1d).register(meterRegistry);

        assertThat(toMap(meterRegistry.eventFor(gauge))).isEqualTo(expected);
    }

    @Test
    void eventFor_Gauge_withMeterLevelTags() {
        eventFor_Gauge_withMeterLevelTags(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myGauge")
                .put("meter_tag_key", "meter_tag_value")
                .put("value", 1)
                .build()
        );

        eventFor_Gauge_withMeterLevelTags(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("meter_tag_key", "meter_tag_value")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Gauge_withMeterLevelTags(NewRelicMeterRegistry meterRegistry, Map<String, Object> expected) {
        Gauge gauge = Gauge.builder("my.gauge", () -> 1d)
                .tag("meter_tag_key", "meter_tag_value")
                .register(meterRegistry);

        assertThat(toMap(meterRegistry.eventFor(gauge))).isEqualTo(expected);
    }

    @Test
    void eventFor_Gauge_withBothLevelTags() {
        eventFor_Gauge_withBothLevelTags(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myGauge")
                .put("meter_tag_key", "meter_tag_value")
                .put("registry_tag_key", "registry_tag_value")
                .put("value", 1)
                .build()
        );

        eventFor_Gauge_withBothLevelTags(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("meter_tag_key", "meter_tag_value")
                .put("registry_tag_key", "registry_tag_value")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Gauge_withBothLevelTags(NewRelicMeterRegistry meterRegistry, Map<String, Object> expected) {
        meterRegistry.config().commonTags("registry_tag_key", "registry_tag_value");
        Gauge gauge = Gauge.builder("my.gauge", () -> 1d)
                .tag("meter_tag_key", "meter_tag_value")
                .register(meterRegistry);

        assertThat(toMap(meterRegistry.eventFor(gauge))).isEqualTo(expected);
    }

    @Test
    void eventFor_Gauge_nanValuesDropped() {
        eventFor_Gauge_nanValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_Gauge_nanValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_Gauge_nanValuesDropped(NewRelicMeterRegistry registry) {
        Gauge gauge = Gauge.builder("my.gauge.nan", () -> NaN).register(registry);

        assertThat(registry.eventFor(gauge)).isNull();
    }

    @Test
    void eventFor_Gauge_infiniteValuesDropped() {
        eventFor_Gauge_infiniteValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_Gauge_infiniteValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_Gauge_infiniteValuesDropped(NewRelicMeterRegistry registry) {
        Gauge positive = Gauge.builder("my.gauge.positive", () -> POSITIVE_INFINITY).register(registry);
        assertThat(registry.eventFor(positive)).isNull();

        Gauge negative = Gauge.builder("my.gauge.negative", () -> NEGATIVE_INFINITY).register(registry);
        assertThat(registry.eventFor(negative)).isNull();
    }

    @Test
    void eventFor_Counter() {
        eventFor_Counter(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myCounter")
                .put("throughput", 1)
                .build()
        );

        eventFor_Counter(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myCounter")
                .put("metricType", "COUNTER")
                .put("throughput", 1)
                .build()
        );
    }

    private void eventFor_Counter(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        Counter counter = registry.counter("my.counter");

        counter.increment();
        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(counter))).isEqualTo(expected);
    }

    @Test
    void eventFor_Timer() {
        eventFor_Timer(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myTimer")
                .put("timeUnit", "seconds")
                .put("count", 1)
                .put("avg", 1)
                .put("max", 1)
                .put("totalTime", 1)
                .build()
        );

        eventFor_Timer(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myTimer")
                .put("metricType", "TIMER")
                .put("timeUnit", "seconds")
                .put("count", 1)
                .put("avg", 1)
                .put("max", 1)
                .put("totalTime", 1)
                .build()
        );
    }

    private void eventFor_Timer(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        Timer timer = registry.timer("my.timer");

        timer.record(Duration.ofSeconds(1));
        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(timer))).isEqualTo(expected);
    }

    @Test
    void eventFor_DistributionSummary() {
        eventFor_DistributionSummary(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "mySummary")
                .put("count", 1)
                .put("avg", 1)
                .put("max", 1)
                .put("total", 1)
                .build()
        );

        eventFor_DistributionSummary(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "mySummary")
                .put("metricType", "DISTRIBUTION_SUMMARY")
                .put("count", 1)
                .put("avg", 1)
                .put("max", 1)
                .put("total", 1)
                .build()
        );
    }

    private void eventFor_DistributionSummary(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        DistributionSummary summary = DistributionSummary.builder("my.summary")
                .publishPercentileHistogram()
                .register(registry);

        summary.record(1);
        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(summary))).isEqualTo(expected);
    }

    @Test
    void eventFor_LongTaskTimer() {
        eventFor_LongTaskTimer(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myLongTaskTimer")
                .put("timeUnit", "seconds")
                .put("duration", 60)
                .put("activeTasks", 1)
                .build()
        );

        eventFor_LongTaskTimer(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myLongTaskTimer")
                .put("metricType", "LONG_TASK_TIMER")
                .put("timeUnit", "seconds")
                .put("duration", 60)
                .put("activeTasks", 1)
                .build()
        );
    }

    private void eventFor_LongTaskTimer(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        LongTaskTimer timer = LongTaskTimer.builder("my.long.task.timer")
                .register(registry);

        timer.start();
        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(timer))).isEqualTo(expected);
    }

    @Test
    void eventFor_TimeGauge() {
        eventFor_TimeGauge(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myTimeGauge")
                .put("timeUnit", "seconds")
                .put("value", 1)
                .build()
        );

        eventFor_TimeGauge(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myTimeGauge")
                .put("metricType", "GAUGE")
                .put("timeUnit", "seconds")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_TimeGauge(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        TimeGauge timeGauge = registry.more().timeGauge("my.time.gauge", Tags.empty(), obj, SECONDS, AtomicReference::get);

        assertThat(toMap(registry.eventFor(timeGauge))).isEqualTo(expected);
    }

    @Test
    void eventFor_TimeGauge_nanValuesDropped() {
        eventFor_TimeGauge_nanValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_TimeGauge_nanValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_TimeGauge_nanValuesDropped(NewRelicMeterRegistry registry) {
        AtomicReference<Double> obj = new AtomicReference<>(NaN);
        TimeGauge timeGauge = registry.more().timeGauge("my.time.gauge.nan", Tags.empty(), obj, SECONDS, AtomicReference::get);

        assertThat(registry.eventFor(timeGauge)).isNull();
    }

    @Test
    void eventFor_TimeGauge_infiniteValuesDropped() {
        eventFor_TimeGauge_infiniteValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_TimeGauge_infiniteValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_TimeGauge_infiniteValuesDropped(NewRelicMeterRegistry registry) {
        AtomicReference<Double> positiveInfinity = new AtomicReference<>(POSITIVE_INFINITY);
        TimeGauge positive = registry.more().timeGauge("my.time.gauge.positive", Tags.empty(), positiveInfinity, SECONDS, AtomicReference::get);

        assertThat(registry.eventFor(positive)).isNull();


        AtomicReference<Double> negativeInfinity = new AtomicReference<>(NEGATIVE_INFINITY);
        TimeGauge negative = registry.more().timeGauge("my.time.gauge.negative", Tags.empty(), negativeInfinity, SECONDS, AtomicReference::get);

        assertThat(registry.eventFor(negative)).isNull();
    }

    @Test
    void eventFor_FunctionCounter() {
        eventFor_FunctionCounter(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myFunctionCounter")
                .put("throughput", 1)
                .build()
        );

        eventFor_FunctionCounter(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myFunctionCounter")
                .put("metricType", "COUNTER")
                .put("throughput", 1)
                .build()
        );
    }

    private void eventFor_FunctionCounter(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        FunctionCounter counter = FunctionCounter.builder("my.function.counter", 1d, Number::doubleValue).register(registry);
        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(counter))).isEqualTo(expected);
    }

    @Test
    void eventFor_FunctionCounter_infiniteValuesDropped() {
        eventFor_FunctionCounter_infiniteValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_FunctionCounter_infiniteValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_FunctionCounter_infiniteValuesDropped(NewRelicMeterRegistry registry) {
        FunctionCounter positive = FunctionCounter.builder("my.function.counter.positive", POSITIVE_INFINITY, Number::doubleValue)
                .register(registry);

        clock.add(meterNameEventTypeDisabledConfig.step());
        assertThat(registry.eventFor(positive)).isNull();

        FunctionCounter negative = FunctionCounter.builder("my.function.counter.negative", NEGATIVE_INFINITY, Number::doubleValue)
                .register(registry);

        clock.add(meterNameEventTypeDisabledConfig.step());
        assertThat(registry.eventFor(negative)).isNull();
    }

    @Test
    void eventFor_FunctionTimer() {
        eventFor_FunctionTimer(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myFunctionTimer")
                .put("timeUnit", "seconds")
                .put("totalTime", 2)
                .put("count", 1)
                .put("avg", 2)
                .build()
        );

        eventFor_FunctionTimer(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myFunctionTimer")
                .put("metricType", "TIMER")
                .put("timeUnit", "seconds")
                .put("totalTime", 2)
                .put("count", 1)
                .put("avg", 2)
                .build()
        );
    }

    private void eventFor_FunctionTimer(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        FunctionTimer timer = registry.more().timer("my.function.timer", emptyList(), new Object(), o -> 1L, o -> 2.0, registry.getBaseTimeUnit());

        clock.add(meterNameEventTypeDisabledConfig.step());

        assertThat(toMap(registry.eventFor(timer))).isEqualTo(expected);
    }

    @Test
    void eventFor_Meter() {
        eventFor_Meter(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myMeter")
                .put("value", 1)
                .build()
        );

        eventFor_Meter(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myMeter")
                .put("metricType", "GAUGE")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Meter(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        List<Measurement> measurements = Collections.singletonList(new Measurement(() -> 1d, Statistic.VALUE));

        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        assertThat(toMap(registry.eventFor(meter))).isEqualTo(expected);
    }

    @Test
    void eventFor_Meter_nanAndInfiniteValuesDropped() {
        eventFor_Meter_nanAndInfiniteValuesDropped(this.meterNameEventTypeEnabledRegistry);
        eventFor_Meter_nanAndInfiniteValuesDropped(this.meterNameEventTypeDisabledRegistry);
    }

    private void eventFor_Meter_nanAndInfiniteValuesDropped(NewRelicMeterRegistry registry) {
        List<Measurement> measurements = Arrays.asList(
                new Measurement(() -> POSITIVE_INFINITY, Statistic.VALUE),
                new Measurement(() -> NEGATIVE_INFINITY, Statistic.VALUE),
                new Measurement(() -> NaN, Statistic.VALUE)
        );

        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        assertThat(registry.eventFor(meter)).isNull();
    }

    @Test
    void eventFor_Meter_nanAndInfiniteValuesDropped_finiteValuesKept() {
        eventFor_Meter_nanAndInfiniteValuesDropped_finiteValuesKept(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myMeter")
                .put("value", 1)
                .build()
        );

        eventFor_Meter_nanAndInfiniteValuesDropped_finiteValuesKept(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myMeter")
                .put("metricType", "GAUGE")
                .put("value", 1)
                .build()
        );
    }

    private void eventFor_Meter_nanAndInfiniteValuesDropped_finiteValuesKept(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        List<Measurement> measurements = Arrays.asList(
                new Measurement(() -> POSITIVE_INFINITY, Statistic.VALUE),
                new Measurement(() -> NEGATIVE_INFINITY, Statistic.VALUE),
                new Measurement(() -> NaN, Statistic.VALUE),
                new Measurement(() -> 1d, Statistic.VALUE)
        );

        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        assertThat(toMap(registry.eventFor(meter))).isEqualTo(expected);
    }

    @Test
    void eventFor_Meter_onlyLastValueKeptForDuplicateKeys() {
        eventFor_Meter_onlyLastValueKeptForDuplicateKeys(this.meterNameEventTypeEnabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "myMeter")
                .put("value", 2)
                .build()
        );

        eventFor_Meter_onlyLastValueKeptForDuplicateKeys(this.meterNameEventTypeDisabledRegistry, ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myMeter")
                .put("metricType", "GAUGE")
                .put("value", 2)
                .build()
        );
    }

    private void eventFor_Meter_onlyLastValueKeptForDuplicateKeys(NewRelicMeterRegistry registry, Map<String, Object> expected) {
        List<Measurement> measurements = Arrays.asList(
                new Measurement(() -> 3d, Statistic.VALUE),
                new Measurement(() -> 1d, Statistic.VALUE),
                new Measurement(() -> 2d, Statistic.VALUE)
        );

        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        assertThat(toMap(registry.eventFor(meter))).isEqualTo(expected);
    }

    @Test
    void publishViaApi() throws Throwable {
        MockNewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public NewRelicIntegration integration() {
                return NewRelicIntegration.API;
            }

        };

        ArgumentCaptor<HttpSender.Request> captor = ArgumentCaptor.forClass(HttpSender.Request.class);
        doReturn(new HttpSender.Response(200, "body")).when(httpSender).send(captor.capture());

        NewRelicMeterRegistry registry = NewRelicMeterRegistry.builder(config)
                .clock(clock)
                .threadFactory(threadFactory)
                .httpClient(httpSender)
                .insights(insights)
                .build();

        Gauge.builder("my.gauge", () -> 1d).register(registry);

        registry.publish();

        List<Map<String, Object>> published = jsonListToMap(new String(captor.getValue().getEntity()));

        assertThat(published).containsExactly(ImmutableMap.<String, Object>builder()
                .put("eventType", "MicrometerSample")
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("value", 1)
                .build()
        );

        verifyNoInteractions(insights);
    }

    @Test
    void publishViaApiFailure() throws Throwable {
        MockNewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public NewRelicIntegration integration() {
                return NewRelicIntegration.API;
            }

        };

        doThrow(new RuntimeException("BOOM!")).when(httpSender).send(any(HttpSender.Request.class));

        NewRelicMeterRegistry registry = NewRelicMeterRegistry.builder(config)
                .clock(clock)
                .threadFactory(threadFactory)
                .httpClient(httpSender)
                .insights(insights)
                .build();

        Gauge.builder("my.gauge", () -> 1d).register(registry);

        // verify it throws no exception, the test will fail if it does
        registry.publish();

        verify(httpSender).send(any(HttpSender.Request.class));
        verifyNoInteractions(insights);
    }

    @Test
    void publishViaApm() {
        MockNewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public NewRelicIntegration integration() {
                return NewRelicIntegration.APM;
            }

        };

        NewRelicMeterRegistry registry = NewRelicMeterRegistry.builder(config)
                .clock(clock)
                .threadFactory(threadFactory)
                .httpClient(httpSender)
                .insights(insights)
                .build();

        Gauge.builder("my.gauge", () -> 1d).register(registry);

        registry.publish();

        verify(insights).recordCustomEvent("MicrometerSample", ImmutableMap.<String, Object>builder()
                .put("metricName", "myGauge")
                .put("metricType", "GAUGE")
                .put("value", 1d)
                .build());

        verifyNoInteractions(httpSender);
    }

    @Test
    void publishViaApmFailure() {
        MockNewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public NewRelicIntegration integration() {
                return NewRelicIntegration.APM;
            }

        };

        doThrow(new RuntimeException("BOOM!")).when(insights).recordCustomEvent(anyString(), anyMap());

        NewRelicMeterRegistry registry = NewRelicMeterRegistry.builder(config)
                .clock(clock)
                .threadFactory(threadFactory)
                .httpClient(httpSender)
                .insights(insights)
                .build();

        Gauge.builder("my.gauge", () -> 1d).register(registry);

        // verify it throws no exception, the test will fail if it does
        registry.publish();

        verify(insights).recordCustomEvent(anyString(), anyMap());
        verifyNoInteractions(httpSender);
    }

    @Test
    void builder() {
        NewRelicConfig config = new MockNewRelicConfig();

        NewRelicMeterRegistry built = NewRelicMeterRegistry.builder(config)
                .threadFactory(new NamedThreadFactory("MOCK"))
                .insights(insights)
                .clock(clock)
                .httpClient(new HttpUrlConnectionSender())
                .build();

        assertThat(built).isNotNull();
    }

    @Test
    void builder_nullConfig() {
        assertThatThrownBy(() -> NewRelicMeterRegistry.builder(null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void startDisabled() {
        NewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public boolean enabled() {
                return false;
            }
        };

        NewRelicMeterRegistry registry = NewRelicMeterRegistry.builder(config)
                .clock(clock)
                .threadFactory(threadFactory)
                .httpClient(httpSender)
                .insights(insights)
                .build();

        try {
            registry.start(threadFactory);
        } finally {
            registry.close();
        }

        verifyNoInteractions(httpSender);
        verifyNoInteractions(insights);
    }

    @Test
    void configEmptyEventType() {
        NewRelicConfig config = new MockNewRelicConfig() {

            @Override
            public String eventType() {
                return "";
            }

        };

        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void configInvalidAccountId() {
        NewRelicConfig nullConfig = new MockNewRelicConfig() {

            @Override
            public String accountId() {
                return null;
            }

        };

        NewRelicConfig emptyConfig = new MockNewRelicConfig() {

            @Override
            public String accountId() {
                return "";
            }

        };

        for (NewRelicConfig config : new NewRelicConfig[]{nullConfig, emptyConfig}) {
            assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                    .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                    .hasMessageContaining("accountId");
        }
    }

    @Test
    void configInvalidApiKey() {
        NewRelicConfig nullConfig = new MockNewRelicConfig() {

            @Override
            public String apiKey() {
                return null;
            }

        };

        NewRelicConfig emptyConfig = new MockNewRelicConfig() {

            @Override
            public String apiKey() {
                return "";
            }

        };

        for (NewRelicConfig config : new NewRelicConfig[]{nullConfig, emptyConfig}) {
            assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                    .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                    .hasMessageContaining("apiKey");
        }
    }

    @Test
    void configInvalidUri() {
        NewRelicConfig nullConfig = new MockNewRelicConfig() {

            @Override
            public String uri() {
                return null;
            }

        };

        NewRelicConfig emptyConfig = new MockNewRelicConfig() {

            @Override
            public String uri() {
                return "";
            }

        };

        for (NewRelicConfig config : new NewRelicConfig[]{nullConfig, emptyConfig}) {
            assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                    .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                    .hasMessageContaining("uri");
        }
    }

    private Map<String, Object> toMap(NewRelicMeterRegistry.NewRelicEvent event) {
        return jsonObjectToMap(event.toJson());
    }

    private List<Map<String, Object>> jsonListToMap(String json) {
        //noinspection Convert2Diamond
        return readJson(json, new TypeReference<List<Map<String, Object>>>() {
            // this space intentionally left blank
        });
    }

    private Map<String, Object> jsonObjectToMap(String json) {
        //noinspection Convert2Diamond
        return readJson(json, new TypeReference<Map<String, Object>>() {
            // this space intentionally left blank
        });
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON string into " + type.getType() + ": " + json, e);
        }
    }

    private static class MockNewRelicConfig implements NewRelicConfig {

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

    }

}
