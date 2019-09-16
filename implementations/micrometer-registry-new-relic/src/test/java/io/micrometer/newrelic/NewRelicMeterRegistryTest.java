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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link NewRelicMeterRegistry}.
 *
 * @author Johnny Lim
 */
class NewRelicMeterRegistryTest {

    private final NewRelicConfig config = new NewRelicConfig() {

        @Override
		public boolean meterNameEventTypeEnabled() {
        	//Default is false. Publish all metrics under a single eventType
			return NewRelicConfig.super.meterNameEventTypeEnabled();
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
	
    private final NewRelicConfig meterNameEventTypeEnabledConfig = new NewRelicConfig() {

        @Override
		public boolean meterNameEventTypeEnabled() {
        	//Previous behavior for backward compatibility
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
        meterNameEventTypeEnabledRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterNameEventTypeEnabledRegistry.find("my.gauge").gauge(); 
        Stream<String> streamResult = meterNameEventTypeEnabledRegistry.writeGauge(gauge);
        assertThat(streamResult).contains("{\"eventType\":\"myGauge\",\"value\":1}");
        
        registry.gauge("my.gauge", 1d);
        gauge = registry.find("my.gauge").gauge(); 
        streamResult = registry.writeGauge(gauge);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"metricName\":\"myGauge\",\"metricType\":\"GAUGE\"}");
        
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterNameEventTypeEnabledRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterNameEventTypeEnabledRegistry.find("my.gauge").gauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeGauge(gauge)).isEmpty();
        
        registry.gauge("my.gauge", Double.NaN);
        gauge = registry.find("my.gauge").gauge();
        assertThat(registry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterNameEventTypeEnabledRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterNameEventTypeEnabledRegistry.find("my.gauge").gauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeGauge(gauge)).isEmpty();

        meterNameEventTypeEnabledRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterNameEventTypeEnabledRegistry.find("my.gauge").gauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeGauge(gauge)).isEmpty();
        
        registry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        gauge = registry.find("my.gauge").gauge();
        assertThat(registry.writeGauge(gauge)).isEmpty();

        registry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = registry.find("my.gauge").gauge();
        assertThat(registry.writeGauge(gauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterNameEventTypeEnabledRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterNameEventTypeEnabledRegistry.find("my.timeGauge").timeGauge();
        Stream<String> streamResult = meterNameEventTypeEnabledRegistry.writeTimeGauge(timeGauge);
        assertThat(streamResult).contains("{\"eventType\":\"myTimeGauge\",\"value\":1,\"timeUnit\":\"seconds\"}");
        
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        streamResult = registry.writeTimeGauge(timeGauge);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"timeUnit\":\"seconds\",\"metricName\":\"myTimeGauge\",\"metricType\":\"GAUGE\"}");
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterNameEventTypeEnabledRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterNameEventTypeEnabledRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeTimeGauge(timeGauge)).isEmpty();
        
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(registry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeGaugeWithTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterNameEventTypeEnabledRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterNameEventTypeEnabledRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterNameEventTypeEnabledRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterNameEventTypeEnabledRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterNameEventTypeEnabledRegistry.writeTimeGauge(timeGauge)).isEmpty();
        
        obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(registry.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = registry.find("my.timeGauge").timeGauge();
        assertThat(registry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    void writeCounterWithFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterNameEventTypeEnabledRegistry);
        clock.add(config.step());
        Stream<String> streamResult = meterNameEventTypeEnabledRegistry.writeFunctionCounter(counter);
        assertThat(streamResult).contains("{\"eventType\":\"myCounter\",\"throughput\":1}");
        
        counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(config.step());
        streamResult = registry.writeFunctionCounter(counter);
        assertThat(streamResult).contains("{\"eventType\":\"MicrometerSample\",\"throughput\":1,\"metricName\":\"myCounter\",\"metricType\":\"COUNTER\"}");
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue).register(meterNameEventTypeEnabledRegistry);
        clock.add(config.step());
        assertThat(meterNameEventTypeEnabledRegistry.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue).register(meterNameEventTypeEnabledRegistry);
        clock.add(config.step());
        assertThat(meterNameEventTypeEnabledRegistry.writeFunctionCounter(counter)).isEmpty();
        
        counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterNameEventTypeEnabledRegistry);
        assertThat(meterNameEventTypeEnabledRegistry.writeMeter(meter)).isEmpty();
        
        meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4, measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterNameEventTypeEnabledRegistry);
        assertThat(meterNameEventTypeEnabledRegistry.writeMeter(meter)).contains("{\"eventType\":\"myMeter\",\"value\":1,\"value\":2}");
        
        meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).contains("{\"eventType\":\"MicrometerSample\",\"value\":1,\"value\":2,\"metricName\":\"myMeter\",\"metricType\":\"GAUGE\"}");
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
    void configMissing() {
    	try {
    		NewRelicConfig missingEventTypeConfig = new NewRelicConfig() {
				@Override
				public  String eventType() {
					return "";
				}
				@Override
				public String get(String key) {
					return null;
				}
			};
			new NewRelicMeterRegistry(missingEventTypeConfig, clock);
 		
    		assertTrue(false);	
    	} catch(MissingRequiredConfigurationException mrce) {
    		assertTrue(true);
    	} catch (Exception e) {
    		assertTrue(false);
    	}
    	
    	try {
    		NewRelicConfig missingAccountIdConfig = new NewRelicConfig() {
				@Override
				public  String eventType() {
					return "eventType";
				}
    			@Override
				public  String accountId() {
					return null;
				}
				@Override
				public String get(String key) {
					return null;
				}
			};
			new NewRelicMeterRegistry(missingAccountIdConfig, clock);
 		
    		assertTrue(false);	
    	} catch(MissingRequiredConfigurationException mrce) {
    		assertTrue(true);
    	} catch (Exception e) {
    		assertTrue(false);
    	}
    	
    	try {
    		NewRelicConfig missingApiKeyConfig = new NewRelicConfig() {
				@Override
				public  String eventType() {
					return "eventType";
				}
    			@Override
				public  String accountId() {
					return "accountId";
				}
    			@Override
				public  String apiKey() {
					return "";
				}
				@Override
				public String get(String key) {
					return null;
				}
			};
			new NewRelicMeterRegistry(missingApiKeyConfig, clock);
 		
    		assertTrue(false);	
    	} catch(MissingRequiredConfigurationException mrce) {
    		assertTrue(true);
    	} catch (Exception e) {
    		assertTrue(false);
    	}
    	
    	try {
    		NewRelicConfig missingUriConfig = new NewRelicConfig() {
				@Override
				public  String eventType() {
					return "eventType";
				}
    			@Override
				public  String accountId() {
					return "accountId";
				}
    			@Override
				public  String apiKey() {
					return "apiKey";
				}
    			@Override
				public  String uri() {
					return "";
				}
				@Override
				public String get(String key) {
					return null;
				}
			};
			new NewRelicMeterRegistry(missingUriConfig, clock);
 		
    		assertTrue(false);	
    	} catch(MissingRequiredConfigurationException mrce) {
    		assertTrue(true);
    	} catch (Exception e) {
    		assertTrue(false);
    	}     	
    }

    class MockHttpSender implements HttpSender {

    	private Request request;
    	
		@Override
		public Response send(Request request) throws Throwable {
			this.request=request;
			return new Response(200, "body");
		}

		public Request getRequest() {
			return request;
		}
    	
    }
}
