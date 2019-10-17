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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
import io.micrometer.core.instrument.util.NamedThreadFactory;
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
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        //test Http clientProvider
        Stream<String> streamResult = getHttpClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge);
        assertThat(streamResult).contains("{\"eventType\":\"myGauge\",\"value\":1}");

        streamResult = getHttpClientProvider(httpConfig).writeGauge(gauge);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");

        //test Agent clientProvider
        Map<String, Object> result = getAgentClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge); 
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("value", 1);
        
        result = getAgentClientProvider(agentConfig).writeGauge(gauge); 
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("metricName", "myGauge");
        assertThat(result).containsEntry("metricType", "GAUGE");
        assertThat(result).containsEntry("value", 1);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        registry.gauge("my.gauge", Double.NaN);
        Gauge gauge = registry.find("my.gauge").gauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeGauge(gauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        registry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = registry.find("my.gauge").gauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeGauge(gauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeGauge(gauge)).isEmpty();

        registry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = registry.find("my.gauge").gauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeGauge(gauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeGauge(gauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        //test Http clientProvider
        Stream<String> streamResult = getHttpClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge);
        assertThat(streamResult).contains("{\"eventType\":\"myTimeGauge\",\"value\":1000,\"timeUnit\":\"milliseconds\"}");  
        
        streamResult = getHttpClientProvider(httpConfig).writeTimeGauge(timeGauge);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":1000,\"timeUnit\":\"milliseconds\",\"metricName\":\"myTimeGauge\",\"metricType\":\"GAUGE\"}");

        //test Agent clientProvider
        Map<String, Object> result = getAgentClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge);
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("timeUnit", "milliseconds");
        assertThat(result).containsEntry("value", 1000); 
        
        result = getAgentClientProvider(agentConfig).writeTimeGauge(timeGauge);
        assertThat(result).hasSize(4);
        assertThat(result).containsEntry("metricName", "myTimeGauge");
        assertThat(result).containsEntry("metricType", "GAUGE");
        assertThat(result).containsEntry("timeUnit", "milliseconds");
        assertThat(result).containsEntry("value", 1000);
    }
    
    @Test
    void writeGaugeWithTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeTimeGauge(timeGauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = registry.find("my.timeGauge").timeGauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeTimeGauge(timeGauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeTimeGauge(timeGauge)).isEmpty();
        
        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeTimeGauge(timeGauge)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeTimeGauge(timeGauge)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeCounterWithFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(agentConfig.step());
        //test Http clientProvider
        Stream<String> streamResult = getHttpClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter);
        assertThat(streamResult).contains("{\"eventType\":\"myCounter\",\"throughput\":1}"); 

        streamResult = getHttpClientProvider(httpConfig).writeFunctionCounter(counter);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"throughput\":1,\"metricName\":\"myCounter\",\"metricType\":\"COUNTER\"}");

        //test Agent clientProvider
        Map<String, Object> result = getAgentClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter);
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("throughput", 1); 
        
        result = getAgentClientProvider(agentConfig).writeFunctionCounter(counter);
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("metricName", "myCounter");
        assertThat(result).containsEntry("metricType", "COUNTER");
        assertThat(result).containsEntry("throughput", 1);
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(agentConfig.step());
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeFunctionCounter(counter)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(httpConfig.step());
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeFunctionCounter(counter)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeFunctionCounter(counter)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        //test Http clientProvider
        assertThat(getHttpClientProvider(meterNameEventTypeEnabledConfig).writeMeter(meter)).isEmpty();
        assertThat(getHttpClientProvider(httpConfig).writeMeter(meter)).isEmpty();
        
        //test Agent clientProvider
        assertThat(getAgentClientProvider(meterNameEventTypeEnabledConfig).writeMeter(meter)).isEmpty();
        assertThat(getAgentClientProvider(agentConfig).writeMeter(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);
        //test Http clientProvider
        Stream<String> streamResult = getHttpClientProvider(meterNameEventTypeEnabledConfig).writeMeter(meter);
        assertThat(streamResult).contains("{\"eventType\":\"myMeter\",\"value\":1}"); 
        
        streamResult = getHttpClientProvider(httpConfig).writeMeter(meter);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");
        
        //test Agent clientProvider
        Map<String, Object> result = getAgentClientProvider(meterNameEventTypeEnabledConfig).writeMeter(meter);
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("value", 1);
        
        result = getAgentClientProvider(agentConfig).writeMeter(meter);
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("metricName", "myMeter");
        assertThat(result).containsEntry("metricType", "GAUGE");
        assertThat(result).containsEntry("value", 1);
    }
  
    @Test
    void writeMeterWhenCustomMeterHasDuplicatesKeysShouldWriteOnlyLastValue() {
        Measurement measurement1 = new Measurement(() -> 3d, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        //test Http clientProvider
        Stream<String> streamResult = getHttpClientProvider(httpConfig).writeMeter(meter);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}"); 
        
        //test Agent clientProvider
        Map<String, Object> result = getAgentClientProvider(agentConfig).writeMeter(meter);
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("metricName", "myMeter");
        assertThat(result).containsEntry("metricType", "GAUGE");
        assertThat(result).containsEntry("value", 2);
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
               
        httpProvider.sendEvents(gauge.getId(), httpProvider.writeGauge(gauge));

        assertThat(new String(mockHttpClient.getRequest().getEntity()))
                        .contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
        
        //test meterNameEventTypeEnabledConfig = true
        mockHttpClient = new MockHttpSender();
        httpProvider = new NewRelicHttpClientProviderImpl(
                                meterNameEventTypeEnabledConfig, mockHttpClient, registry.config().namingConvention());
        
        registry.gauge("my.gauge2", 1d);
        gauge = registry.find("my.gauge2").gauge();
        
        httpProvider.sendEvents(gauge.getId(), httpProvider.writeGauge(gauge));
        
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
        
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        
        registry.publish();

        assertThat(new String(mockHttpClient.getRequest().getEntity()))
                        .contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");        
    }

    @Test
    void publishWithAgentClientProvider() {
        //test meterNameEventTypeEnabledConfig = false (default)
        MockNewRelicAgent mockNewRelicAgent = new MockNewRelicAgent();
        NewRelicAgentClientProviderImpl agentProvider = new NewRelicAgentClientProviderImpl(
                                                agentConfig, mockNewRelicAgent, registry.config().namingConvention());
        
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(agentConfig, agentProvider, clock);
        
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();       
        
        registry.publish();
        
        assertThat(((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getEventType()).isEqualTo("MicrometerSample");
        Map<String, ?> result = ((MockNewRelicInsights)mockNewRelicAgent.getInsights()).getInsightData().getAttributes();
        assertThat(result).hasSize(3);       
    }
    
    @Test
    void failsConfigMissingClientProvider() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String get(String key) {
                return null;
            }
        };
        
        @SuppressWarnings("deprecation")
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            new NewRelicMeterRegistry(config, null, new NewRelicNamingConvention(), clock, new NamedThreadFactory("test"));
        });
        assertThat(exception.getMessage()).contains("clientProvider");
    }
    
    @Test
    void failsConfigMissingNamingConvention() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String get(String key) {
                return null;
            }
        };
        
        @SuppressWarnings("deprecation")
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            new NewRelicMeterRegistry(config, new MockClientProvider(), null, clock, new NamedThreadFactory("test"));
        });
        assertThat(exception.getMessage()).contains("namingConvention");
    }
    
    @Test
    void failsConfigMissingThreadFactory() {
        NewRelicConfig config = new NewRelicConfig() {
            @Override
            public String get(String key) {
                return null;
            }
        };
        
        @SuppressWarnings("deprecation")
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            new NewRelicMeterRegistry(config, new MockClientProvider(), new NewRelicNamingConvention(), clock, null);
        });
        assertThat(exception.getMessage()).contains("threadFactory");
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
        
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            getHttpClientProvider(config);
        });
        assertThat(exception.getMessage()).contains("eventType");
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
        
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            getHttpClientProvider(config);
        });
        assertThat(exception.getMessage()).contains("accountId");
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
        
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            getHttpClientProvider(config);
        });
        assertThat(exception.getMessage()).contains("apiKey");
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
        
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            getHttpClientProvider(config);
        });
        assertThat(exception.getMessage()).contains("uri");
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
        
        Exception exception = assertThrows(MissingRequiredConfigurationException.class, () -> {
            getAgentClientProvider(config);
        });
        assertThat(exception.getMessage()).contains("eventType");
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
        public void publish(NewRelicMeterRegistry meterRegistry, List<Meter> meters) {
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
