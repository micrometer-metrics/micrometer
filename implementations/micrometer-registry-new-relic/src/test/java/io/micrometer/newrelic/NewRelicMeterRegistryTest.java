/**
 * Copyright 2017 Pivotal Software, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.Config;
import com.newrelic.api.agent.Insights;
import com.newrelic.api.agent.Logger;
import com.newrelic.api.agent.MetricAggregator;
import com.newrelic.api.agent.TraceMetadata;
import com.newrelic.api.agent.TracedMethod;
import com.newrelic.api.agent.Transaction;

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
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.newrelic.NewRelicMeterRegistryTest.MockNewRelicAgent.MockNewRelicInsights;

/**
 * Tests for {@link NewRelicMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Neil Powell
 */
class NewRelicMeterRegistryTest {

    private final NewRelicConfig agentConfig = new NewRelicConfig() {
        @Override
        public String get(String key) {
            return null;
        }
    };  

    private final NewRelicConfig httpConfig = new NewRelicConfig() {
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
    };
    
    private final MockClock clock = new MockClock();
    private final NewRelicMeterRegistry registry = new NewRelicMeterRegistry(httpConfig, new MockClientProvider(), clock);    
    
    NewRelicAgentClientProviderImpl getAgentClientProvider(NewRelicConfig config) {
        return new NewRelicAgentClientProviderImpl(config);
    }
    NewRelicHttpClientProviderImpl getHttpClientProvider(NewRelicConfig config) {
        return new NewRelicHttpClientProviderImpl(config);
    }

    @Test
    void writeGauge() {
        //test Http clientProvider
        writeGauge(meterNameEventTypeEnabledConfig, "{\"eventType\":\"myGauge\",\"value\":1}");
        writeGauge(httpConfig, 
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
        
        //test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        writeGauge(meterNameEventTypeEnabledConfig, expectedEntries);
        expectedEntries.put("metricName", "myGauge2");
        expectedEntries.put("metricType", "GAUGE");
        writeGauge(agentConfig, expectedEntries);
    }

    private void writeGauge(NewRelicConfig config, String expectedJson) {
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(getHttpClientProvider(config).writeGauge(gauge)).containsExactly(expectedJson);
    }
    
    private void writeGauge(NewRelicConfig config, Map<String, Object> expectedEntries) {
        registry.gauge("my.gauge2", 1d);
        Gauge gauge = registry.find("my.gauge2").gauge();
        Map<String, Object> result = getAgentClientProvider(config).writeGauge(gauge);
        assertThat(result).hasSize(expectedEntries.size());
        assertThat(result).containsExactlyEntriesOf(expectedEntries);
    }


    @Test
    void writeGaugeShouldDropNanValue() {
        //test Http clientProvider
        writeGaugeShouldDropNanValue(getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropNanValue(getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeGaugeShouldDropNanValue(getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropNanValue(getAgentClientProvider(agentConfig));
    }
    
    private void writeGaugeShouldDropNanValue(NewRelicHttpClientProviderImpl clientProvider) {
        registry.gauge("my.gauge", Double.NaN);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }
    
    private void writeGaugeShouldDropNanValue(NewRelicAgentClientProviderImpl clientProvider) {
        registry.gauge("my.gauge2", Double.NaN);
        Gauge gauge = registry.find("my.gauge2").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }     

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        //test Http clientProvider
        writeGaugeShouldDropInfiniteValues(getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropInfiniteValues(getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeGaugeShouldDropInfiniteValues(getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeShouldDropInfiniteValues(getAgentClientProvider(agentConfig));
    }

    private void writeGaugeShouldDropInfiniteValues(NewRelicHttpClientProviderImpl clientProvider) {
        registry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();

        registry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = registry.find("my.gauge").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }
    
    private void writeGaugeShouldDropInfiniteValues(NewRelicAgentClientProviderImpl clientProvider) {
        registry.gauge("my.gauge2", Double.POSITIVE_INFINITY);
        Gauge gauge = registry.find("my.gauge2").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();

        registry.gauge("my.gauge2", Double.NEGATIVE_INFINITY);
        gauge = registry.find("my.gauge2").gauge();
        assertThat(clientProvider.writeGauge(gauge)).isEmpty();
    }
    
    @Test
    void writeGaugeWithTimeGauge() {
        //test Http clientProvider
        writeGaugeWithTimeGauge(getHttpClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myTimeGauge\",\"value\":1,\"timeUnit\":\"seconds\"}");
        writeGaugeWithTimeGauge(getHttpClientProvider(httpConfig),
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"timeUnit\":\"seconds\",\"metricName\":\"myTimeGauge\",\"metricType\":\"GAUGE\"}");
        
        //test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        expectedEntries.put("timeUnit", "seconds");
        writeGaugeWithTimeGauge(getAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("metricName", "myTimeGauge2");
        expectedEntries.put("metricType", "GAUGE");
        writeGaugeWithTimeGauge(getAgentClientProvider(agentConfig), expectedEntries);
    }
    
    private void writeGaugeWithTimeGauge(NewRelicHttpClientProviderImpl clientProvider, String expectedJson) {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).containsExactly(expectedJson);       
    }
    
    private void writeGaugeWithTimeGauge(NewRelicAgentClientProviderImpl clientProvider, Map<String, Object> expectedEntries) {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        registry.more().timeGauge("my.timeGauge2", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge2").timeGauge();
        Map<String, Object> result = clientProvider.writeTimeGauge(timeGauge);
        assertThat(result).hasSize(expectedEntries.size());
        assertThat(result).containsExactlyEntriesOf(expectedEntries);
    }
    
    @Test
    void writeGaugeWithTimeGaugeShouldDropNanValue() {
        //test Http clientProvider
        writeGaugeWithTimeGaugeShouldDropNanValue(getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropNanValue(getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeGaugeWithTimeGaugeShouldDropNanValue(getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropNanValue(getAgentClientProvider(agentConfig));
    }
    
    private void writeGaugeWithTimeGaugeShouldDropNanValue(NewRelicHttpClientProviderImpl clientProvider) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }
    
    private void writeGaugeWithTimeGaugeShouldDropNanValue(NewRelicAgentClientProviderImpl clientProvider) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("my.timeGauge2", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge2").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropInfiniteValues() {
        //test Http clientProvider
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(getAgentClientProvider(agentConfig));
    }
    
    private void writeGaugeWithTimeGaugeShouldDropInfiniteValues(NewRelicHttpClientProviderImpl clientProvider) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }
    
    private void writeGaugeWithTimeGaugeShouldDropInfiniteValues(NewRelicAgentClientProviderImpl clientProvider) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge2", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge2").timeGauge();
        assertThat(clientProvider.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeCounterWithFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(httpConfig.step());
        //test Http clientProvider
        writeCounterWithFunctionCounter(counter, getHttpClientProvider(meterNameEventTypeEnabledConfig),
                "{\"eventType\":\"myCounter\",\"throughput\":1}");
        writeCounterWithFunctionCounter(counter, getHttpClientProvider(httpConfig),
                "{\"eventType\":\"MicrometerSample\",\"throughput\":1,\"metricName\":\"myCounter\",\"metricType\":\"COUNTER\"}");
        
        //test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("throughput", 1);
        writeCounterWithFunctionCounter(counter, getAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("metricName", "myCounter");
        expectedEntries.put("metricType", "COUNTER");
        writeCounterWithFunctionCounter(counter, getAgentClientProvider(agentConfig), expectedEntries);
    }

    private void writeCounterWithFunctionCounter(FunctionCounter counter, NewRelicHttpClientProviderImpl clientProvider, String expectedJson) {
        assertThat(clientProvider.writeFunctionCounter(counter)).containsExactly(expectedJson);
    }
    
    private void writeCounterWithFunctionCounter(FunctionCounter counter, NewRelicAgentClientProviderImpl clientProvider, Map<String, Object> expectedEntries) {
        Map<String, Object> result = clientProvider.writeFunctionCounter(counter);
        assertThat(result).hasSize(expectedEntries.size());
        assertThat(result).containsExactlyEntriesOf(expectedEntries);
    }
    
    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        //test Http clientProvider
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeCounterWithFunctionCounterShouldDropInfiniteValues(getAgentClientProvider(agentConfig));
    }

    private void writeCounterWithFunctionCounterShouldDropInfiniteValues(NewRelicHttpClientProviderImpl clientProvider) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
                .register(registry);
        clock.add(httpConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
                .register(registry);
        clock.add(httpConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();
    }
    
    private void writeCounterWithFunctionCounterShouldDropInfiniteValues(NewRelicAgentClientProviderImpl clientProvider) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
                .register(registry);
        clock.add(httpConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
                .register(registry);
        clock.add(httpConfig.step());
        assertThat(clientProvider.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        
        //test Http clientProvider
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
                measurements, getHttpClientProvider(meterNameEventTypeEnabledConfig));
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
                measurements, getHttpClientProvider(httpConfig));
        
        //test Agent clientProvider
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
                measurements, getAgentClientProvider(meterNameEventTypeEnabledConfig));
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
                measurements, getAgentClientProvider(agentConfig));
    }

    private void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
            List<Measurement> measurements, NewRelicHttpClientProviderImpl clientProvider) {
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).isEmpty();
    }
    
    private void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
            List<Measurement> measurements, NewRelicAgentClientProviderImpl clientProvider) {
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
        //test Http clientProvider
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                measurements, getHttpClientProvider(meterNameEventTypeEnabledConfig), 
                "{\"eventType\":\"myMeter\",\"value\":1}");
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                measurements, getHttpClientProvider(httpConfig),
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");
        
        //test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 1);
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                measurements, getAgentClientProvider(meterNameEventTypeEnabledConfig), expectedEntries);
        expectedEntries.put("metricName", "myMeter2");
        expectedEntries.put("metricType", "GAUGE");
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                measurements, getAgentClientProvider(agentConfig), expectedEntries);
    }

    private void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
            List<Measurement> measurements, NewRelicHttpClientProviderImpl clientProvider, String expectedJson) {
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        assertThat(clientProvider.writeMeter(meter)).containsExactly(expectedJson);
    }
    
    private void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
            List<Measurement> measurements, NewRelicAgentClientProviderImpl clientProvider, Map<String, Object> expectedEntries) {
        Meter meter = Meter.builder("my.meter2", Meter.Type.GAUGE, measurements).register(registry);
        Map<String, Object> result = clientProvider.writeMeter(meter);
        assertThat(result).hasSize(expectedEntries.size());
        assertThat(result).containsExactlyEntriesOf(expectedEntries);
    }
  
    @Test
    void writeMeterWhenCustomMeterHasDuplicatesKeysShouldWriteOnlyLastValue() {
        Measurement measurement1 = new Measurement(() -> 3d, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        //test Http clientProvider
        assertThat(getHttpClientProvider(httpConfig).writeMeter(meter)).containsExactly(
                "{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}"); 
        
        //test Agent clientProvider
        Map<String, Object> expectedEntries = new HashMap<>();
        expectedEntries.put("value", 2);
        expectedEntries.put("metricName", "myMeter");
        expectedEntries.put("metricType", "GAUGE");
        Map<String, Object> result = getAgentClientProvider(agentConfig).writeMeter(meter);
        assertThat(result).hasSize(expectedEntries.size());
        assertThat(result).containsExactlyEntriesOf(expectedEntries);
    }
    
    

    @Test
    void sendEventsWithHttpProvider() {
        //test meterNameEventTypeEnabledConfig = false (default)
        MockHttpSender mockHttpClient = new MockHttpSender();
        NewRelicHttpClientProviderImpl httpProvider = new NewRelicHttpClientProviderImpl(
                                                                        httpConfig, mockHttpClient, registry.config().namingConvention());
        
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(httpConfig, httpProvider, clock);
        
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
               
        httpProvider.sendEvents(httpProvider.writeGauge(gauge));

        assertThat(new String(mockHttpClient.getRequest().getEntity()))
                        .contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
        
        //test meterNameEventTypeEnabledConfig = true
        mockHttpClient = new MockHttpSender();
        httpProvider = new NewRelicHttpClientProviderImpl(
                                meterNameEventTypeEnabledConfig, mockHttpClient, registry.config().namingConvention());
        
        registry.gauge("my.gauge2", 1d);
        gauge = registry.find("my.gauge2").gauge();
        
        httpProvider.sendEvents(httpProvider.writeGauge(gauge));
        
        assertThat(new String(mockHttpClient.getRequest().getEntity()))
                                        .contains("{\"eventType\":\"myGauge2\",\"value\":1}");        
    }
    
    @Test
    void sendEventsWithAgentProvider() {        
        //test meterNameEventTypeEnabledConfig = false (default)
        MockNewRelicAgent mockNewRelicAgent = new MockNewRelicAgent();
        NewRelicAgentClientProviderImpl agentProvider = new NewRelicAgentClientProviderImpl(
                                                agentConfig, mockNewRelicAgent, registry.config().namingConvention());
        
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(agentConfig, agentProvider, clock);
        
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
               
        agentProvider.sendEvents(gauge.getId(), agentProvider.writeGauge(gauge));
        
        assertThat(((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getEventType()).isEqualTo("MicrometerSample");
        Map<String, ?> result = ((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getAttributes();
        assertThat(result).hasSize(3);
        
        //test meterNameEventTypeEnabledConfig = true
        mockNewRelicAgent = new MockNewRelicAgent();
        agentProvider = new NewRelicAgentClientProviderImpl(
                                meterNameEventTypeEnabledConfig, mockNewRelicAgent, registry.config().namingConvention());
        
        registry.gauge("my.gauge2", 1d);
        gauge = registry.find("my.gauge2").gauge();
        
        agentProvider.sendEvents(gauge.getId(), agentProvider.writeGauge(gauge));
        
        assertThat(((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getEventType()).isEqualTo("myGauge2");
        result = ((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getAttributes();         
        assertThat(result).hasSize(1);        
    }
    
    @Test
    void publishWithHttpClientProvider() {
        //test meterNameEventTypeEnabledConfig = false (default)
        MockHttpSender mockHttpClient = new MockHttpSender();
        NewRelicHttpClientProviderImpl httpProvider = new NewRelicHttpClientProviderImpl(
                                              httpConfig, mockHttpClient, registry.config().namingConvention());
        
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(httpConfig, httpProvider, clock);
        
        registry.gauge("my.gauge", Tags.of("theTag", "theValue"), 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        
        registry.gauge("other.gauge", 2d);
        Gauge other = registry.find("other.gauge").gauge();
        assertThat(other).isNotNull();
        
        registry.publish();

        //should send a batch of multiple in one json payload
        assertThat(new String(mockHttpClient.getRequest().getEntity()))
                        .contains("[{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"otherGauge\",\"metricType\":\"GAUGE\"}," +
                            "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\",\"theTag\":\"theValue\"}]");
    }

    @Test
    void publishWithAgentClientProvider() {
        //test meterNameEventTypeEnabledConfig = false (default)
        MockNewRelicAgent mockNewRelicAgent = new MockNewRelicAgent();
        NewRelicAgentClientProviderImpl agentProvider = new NewRelicAgentClientProviderImpl(
                                                agentConfig, mockNewRelicAgent, registry.config().namingConvention());
        
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(agentConfig, agentProvider, clock);
        
        registry.gauge("my.gauge", Tags.of("theTag", "theValue"), 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        
        registry.gauge("other.gauge", 2d);
        Gauge other = registry.find("other.gauge").gauge();
        assertThat(other).isNotNull();
        
        registry.publish();
        
        //should delegate to the Agent one at a time
        assertThat(((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getEventType()).isEqualTo("MicrometerSample");
        Map<String, ?> result = ((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getAttributes();
        assertThat(result).hasSize(4);       
    }
    
    @Test
    void failsConfigMissingClientProvider() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String get(String key) {
                return null;
            }
        };
        
        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, null, clock))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessageContaining("clientProvider");
    }
    
    @Test
    void failsConfigHttpMissingEventType() {
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
        
        assertThatThrownBy(() -> getHttpClientProvider(config))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessageContaining("eventType");
    }
    
    @Test
    void succeedsConfigHttpMissingEventType() {
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

        assertThat( getHttpClientProvider(config) ).isNotNull();
    }

    @Test
    void failsConfigHttpMissingAccountId() {
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
        
        assertThatThrownBy(() -> getHttpClientProvider(config))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessageContaining("accountId");
    }
    
    @Test
    void failsConfigHttpMissingApiKey() {
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
        
        assertThatThrownBy(() -> getHttpClientProvider(config))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessageContaining("apiKey");
    }
    
    @Test
    void failsConfigHttpMissingUri() {
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
        
        assertThatThrownBy(() -> getHttpClientProvider(config))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
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
        
        assertThatThrownBy(() -> getAgentClientProvider(config))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessageContaining("eventType");
    }
    
    @Test
    void succeedsConfigAgentMissingEventType() {
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
            public String get(String key) {
                return null;
            }
        };

        assertThat( getAgentClientProvider(config) ).isNotNull();
    }
    
    class MockHttpSender implements HttpSender {
        
        private Request request;
        
        @Override
        public Response send(Request request) throws Throwable {
            this.request = request;
            return new Response(200, "body");
        }
        
        public Request getRequest() {
            return request;
        }
    }

    class MockClientProvider implements NewRelicClientProvider {

        @Override
        public void publish(NewRelicMeterRegistry meterRegistry) {
            //No-op
        }

        @Override
        public Object writeFunctionTimer(FunctionTimer timer) {
            //No-op
            return null;
        }

        @Override
        public Object writeTimer(Timer timer) {
            //No-op
            return null;
        }

        @Override
        public Object writeSummary(DistributionSummary summary) {
            //No-op
            return null;
        }

        @Override
        public Object writeLongTaskTimer(LongTaskTimer timer) {
            //No-op
            return null;
        }

        @Override
        public Object writeTimeGauge(TimeGauge gauge) {
            //No-op
            return null;
        }

        @Override
        public Object writeGauge(Gauge gauge) {
            //No-op
            return null;
        }

        @Override
        public Object writeCounter(Counter counter) {
            //No-op
            return null;
        }

        @Override
        public Object writeFunctionCounter(FunctionCounter counter) {
            //No-op
            return null;
        }

        @Override
        public Object writeMeter(Meter meter) {
            //No-op
            return null;
        }

    }
    
    class MockNewRelicAgent implements Agent {

        private final Insights insights;

        public MockNewRelicAgent() {
            this.insights = new MockNewRelicInsights();
        }

        @Override
        public Config getConfig() {
            //No-op
            return null;
        }

        @Override
        public Insights getInsights() {
            return insights;
        }

        public class MockNewRelicInsights implements Insights {

            private InsightData insightData;

            public InsightData getInsightData() {
                return insightData;
            }

            @Override
            public void recordCustomEvent(String eventType, Map<String, ?> attributes) {
                this.insightData = new InsightData(eventType, attributes);
            }

            public void setInsightData(InsightData insightData) {
                this.insightData = insightData;
            }   
    
            class InsightData {
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

        @Override
        public Logger getLogger() {
            //No-op
            return null;
        }

        @Override
        public MetricAggregator getMetricAggregator() {
            //No-op
            return null;
        }

        @Override
        public TracedMethod getTracedMethod() {
            //No-op
            return null;
        }

        @Override
        public Transaction getTransaction() {
            //No-op
            return null;
        }

        @Override
        public Map<String, String> getLinkingMetadata() {
            //No-op
            return null;
        }

        @Override
        public TraceMetadata getTraceMetadata() {
            //No-op
            return null;
        }    
    }
}
