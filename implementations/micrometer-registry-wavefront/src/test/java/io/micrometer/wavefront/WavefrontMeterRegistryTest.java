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
package io.micrometer.wavefront;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.clients.WavefrontClient;
import com.wavefront.sdk.common.clients.service.token.TokenService;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WavefrontMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Stephane Nicoll
 */
class WavefrontMeterRegistryTest {

    private final WavefrontConfig config = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String uri() {
            return "uri";
        }

        @Override
        public String apiToken() {
            return "apiToken";
        }

        @Override
        public String source() {
            return "host";
        }
    };

    private final MockClock clock = new MockClock();

    private final WavefrontSender wavefrontSender = spy(WavefrontSender.class);

    private final WavefrontMeterRegistry registry = WavefrontMeterRegistry.builder(config)
        .clock(clock)
        .wavefrontSender(wavefrontSender)
        .build();

    @Test
    void publishMetric() throws IOException {
        Meter.Id id = registry.counter("name").getId();
        long time = System.currentTimeMillis();
        registry.publishMetric(id, null, time, 1d);
        verify(wavefrontSender, times(1)).sendMetric("name", 1d, time, "host", Collections.emptyMap());
        verifyNoMoreInteractions(wavefrontSender);
    }

    @Test
    void publishMetricWhenNanOrInfinityShouldNotAdd() {
        Meter.Id id = registry.counter("name").getId();
        registry.publishMetric(id, null, System.currentTimeMillis(), Double.NaN);
        registry.publishMetric(id, null, System.currentTimeMillis(), Double.POSITIVE_INFINITY);
        verifyNoInteractions(wavefrontSender);
    }

    @Test
    void publishMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        registry.publishMeter(meter);
        verifyNoInteractions(wavefrontSender);
    }

    @Test
    void publishMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() throws IOException {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4,
                measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        registry.publishMeter(meter);
        verify(wavefrontSender, times(1)).sendMetric("my.meter", 1d, clock.wallTime(), "host",
                Collections.singletonMap("statistic", "value"));
        verify(wavefrontSender, times(1)).sendMetric("my.meter", 2d, clock.wallTime(), "host",
                Collections.singletonMap("statistic", "value"));
        verifyNoMoreInteractions(wavefrontSender);
    }

    @Test
    void publishDistribution() throws IOException {
        Meter.Id id = registry.summary("name").getId();
        long time = System.currentTimeMillis();
        List<Pair<Double, Integer>> centroids = Arrays.asList(new Pair<>(1d, 1));
        List<WavefrontHistogramImpl.Distribution> distributions = Arrays
            .asList(new WavefrontHistogramImpl.Distribution(time, centroids));
        registry.publishDistribution(id, distributions);
        verify(wavefrontSender, times(1)).sendDistribution("name", centroids,
                Collections.singleton(HistogramGranularity.MINUTE), time, "host", Collections.emptyMap());
        verifyNoMoreInteractions(wavefrontSender);
    }

    @Test
    @Issue("#2173")
    void defaultStepConfigAffectsWavefrontBuilder() {
        WavefrontClient.Builder defaultSenderBuilder = WavefrontMeterRegistry.getDefaultSenderBuilder(config);
        assertThat(defaultSenderBuilder).hasFieldOrPropertyWithValue("flushInterval", 60_000L);
        assertThat(defaultSenderBuilder).hasFieldOrPropertyWithValue("flushIntervalTimeUnit", TimeUnit.MILLISECONDS);
    }

    @Test
    void configureDefaultSenderWithCustomConfig() {
        WavefrontConfig customConfig = new WavefrontConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "https://example.com";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }

            @Override
            public int batchSize() {
                return 20;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(15);
            }
        };
        WavefrontClient.Builder builder = WavefrontMeterRegistry.getDefaultSenderBuilder(customConfig);
        WavefrontClient sender = builder.build();
        assertThat(builder).hasFieldOrPropertyWithValue("flushInterval", 15_000L);
        assertThat(builder).hasFieldOrPropertyWithValue("flushIntervalTimeUnit", TimeUnit.MILLISECONDS);
        assertThat(sender).extracting("metricsReportingService")
            .hasFieldOrPropertyWithValue("uri", URI.create("https://example.com"))
            .extracting("tokenService")
            .isInstanceOf(TokenService.class)
            .returns("apiToken", tokenService -> ((TokenService) tokenService).getToken());
        assertThat(sender).hasFieldOrPropertyWithValue("batchSize", 20);
    }

    @Test
    void failsWithDefaultSenderWhenUriIsMissing() {
        WavefrontConfig missingUriConfig = new WavefrontConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return "fakeToken";
            }
        };

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> WavefrontMeterRegistry.builder(missingUriConfig).build());
    }

    @Test
    void failsWithDefaultSenderWhenApiTokenMissingAndDirectToApi() {
        WavefrontConfig missingApiTokenDirectConfig = WavefrontConfig.DEFAULT_DIRECT;

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> WavefrontMeterRegistry.builder(missingApiTokenDirectConfig).build());
    }

    @Test
    void customSenderDoesNotNeedUri() {
        WavefrontConfig missingUriConfig = new WavefrontConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return "fakeToken";
            }
        };

        assertThatCode(() -> WavefrontMeterRegistry.builder(missingUriConfig)
            .wavefrontSender(mock(WavefrontSender.class))
            .build()).doesNotThrowAnyException();
    }

    @Test
    void customSenderDoesNotNeedApiToken() {
        WavefrontConfig missingApiTokenDirectConfig = WavefrontConfig.DEFAULT_DIRECT;

        assertThatCode(() -> WavefrontMeterRegistry.builder(missingApiTokenDirectConfig)
            .wavefrontSender(mock(WavefrontSender.class))
            .build()).doesNotThrowAnyException();
    }

    @Test
    void proxyConfigDoesNotNeedApiToken() {
        WavefrontConfig missingApiTokenProxyConfig = WavefrontConfig.DEFAULT_PROXY;

        assertThatCode(() -> WavefrontMeterRegistry.builder(missingApiTokenProxyConfig).build())
            .doesNotThrowAnyException();
    }

    @Test
    void proxyUriConvertedToHttp() {
        assertThat(WavefrontMeterRegistry.getWavefrontReportingUri(WavefrontConfig.DEFAULT_PROXY))
            .startsWith("http://");
    }

    @Test
    void directApiUriUnchanged() {
        assertThat(WavefrontMeterRegistry.getWavefrontReportingUri(WavefrontConfig.DEFAULT_DIRECT))
            .isEqualTo(WavefrontConfig.DEFAULT_DIRECT.uri());
    }

    @Test
    @Issue("#3196")
    void wavefrontClientShouldBeClosedOnRegistryClose() throws IOException {
        registry.close();
        verify(wavefrontSender).close();
    }

}
