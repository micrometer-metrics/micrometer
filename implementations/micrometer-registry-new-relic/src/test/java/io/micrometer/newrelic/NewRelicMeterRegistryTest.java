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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;

/**
 * Tests for {@link NewRelicMeterRegistry}.
 *
 * @author Johnny Lim
 */
class NewRelicMeterRegistryTest {

    private final NewRelicConfig config = new NewRelicConfig() {
        
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
    private final NewRelicMeterRegistry meterNameEventTypeEnabledRegistry = new NewRelicMeterRegistry(meterNameEventTypeEnabledConfig, clock);
    private final NewRelicMeterRegistry registry = new NewRelicMeterRegistry(config, clock);

    @Test
    void writeGauge() {
        writeGauge(this.meterNameEventTypeEnabledRegistry, "{\"eventType\":\"myGauge\",\"value\":1}");
        writeGauge(this.registry,
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
    }

    private void writeGauge(NewRelicMeterRegistry meterRegistry, String expectedJson) {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).containsExactly(expectedJson);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        writeGaugeShouldDropNanValue(this.meterNameEventTypeEnabledRegistry);
        writeGaugeShouldDropNanValue(this.registry);
    }

    private void writeGaugeShouldDropNanValue(NewRelicMeterRegistry meterRegistry) {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        writeGaugeShouldDropInfiniteValues(this.meterNameEventTypeEnabledRegistry);
        writeGaugeShouldDropInfiniteValues(this.registry);
    }

    private void writeGaugeShouldDropInfiniteValues(NewRelicMeterRegistry meterRegistry) {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGauge() {
        writeGaugeWithTimeGauge(this.meterNameEventTypeEnabledRegistry,
                "{\"eventType\":\"myTimeGauge\",\"value\":1,\"timeUnit\":\"seconds\"}");
        writeGaugeWithTimeGauge(this.registry,
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"timeUnit\":\"seconds\",\"metricName\":\"myTimeGauge\",\"metricType\":\"GAUGE\"}");
    }

    private void writeGaugeWithTimeGauge(NewRelicMeterRegistry meterRegistry, String expectedJson) {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).containsExactly(expectedJson);
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropNanValue() {
        writeGaugeWithTimeGaugeShouldDropNanValue(this.meterNameEventTypeEnabledRegistry);
        writeGaugeWithTimeGaugeShouldDropNanValue(this.registry);
    }

    private void writeGaugeWithTimeGaugeShouldDropNanValue(NewRelicMeterRegistry meterRegistry) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropInfiniteValues() {
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(this.meterNameEventTypeEnabledRegistry);
        writeGaugeWithTimeGaugeShouldDropInfiniteValues(this.registry);
    }

    private void writeGaugeWithTimeGaugeShouldDropInfiniteValues(NewRelicMeterRegistry meterRegistry) {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeCounterWithFunctionCounter() {
        writeCounterWithFunctionCounter(this.meterNameEventTypeEnabledRegistry,
                "{\"eventType\":\"myCounter\",\"throughput\":1}");
        writeCounterWithFunctionCounter(this.registry,
                "{\"eventType\":\"MicrometerSample\",\"throughput\":1,\"metricName\":\"myCounter\",\"metricType\":\"COUNTER\"}");
    }

    private void writeCounterWithFunctionCounter(NewRelicMeterRegistry meterRegistry, String expectedJson) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).containsExactly(expectedJson);
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        writeCounterWithFunctionCounterShouldDropInfiniteValues(this.meterNameEventTypeEnabledRegistry);
        writeCounterWithFunctionCounterShouldDropInfiniteValues(this.registry);
    }

    private void writeCounterWithFunctionCounterShouldDropInfiniteValues(NewRelicMeterRegistry meterRegistry) {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
                .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
                .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(this.meterNameEventTypeEnabledRegistry);
        writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(this.registry);
    }

    private void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(
            NewRelicMeterRegistry meterRegistry) {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(meterRegistry);
        assertThat(meterRegistry.writeMeter(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                this.meterNameEventTypeEnabledRegistry, "{\"eventType\":\"myMeter\",\"value\":1}");
        writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
                this.registry,
                "{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");
    }

    private void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(
            NewRelicMeterRegistry meterRegistry, String expectedJson) {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(meterRegistry);
        assertThat(meterRegistry.writeMeter(meter)).containsExactly(expectedJson);
    }

    @Test
    void writeMeterWhenCustomMeterHasDuplicatesKeysShouldWriteOnlyLastValue() {
        Measurement measurement1 = new Measurement(() -> 3d, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).containsExactly("{\"eventType\":\"MicrometerSample\",\"value\":2,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");
    }

    @Test
    void publish() {
        MockHttpSender mockHttpSender = new MockHttpSender();
        NewRelicMeterRegistry registry = new NewRelicMeterRegistry(config, clock, new NamedThreadFactory("new-relic-test"), mockHttpSender);
        
        registry.gauge("my.gauge", 1d);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        
        registry.publish();
        
        assertThat(new String(mockHttpSender.getRequest().getEntity()))
            .contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
    }

    @Test
    void configMissingEventType() {
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
        
        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void configMissingAccountId() {
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

        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                .hasMessageContaining("accountId");
    }
    
    @Test
    void configMissingApiKey() {
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

        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                .hasMessageContaining("apiKey");
    }
    
    @Test
    void configMissingUri() {
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

        assertThatThrownBy(() -> new NewRelicMeterRegistry(config, clock))
                .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
                .hasMessageContaining("uri");
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

}
