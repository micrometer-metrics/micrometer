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
package io.micrometer.wavefront;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link WavefrontMeterRegistry}.
 *
 * @author Johnny Lim
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
    void addMetric() throws IOException {
        Meter.Id id = registry.counter("name").getId();
        long time = System.currentTimeMillis();
        registry.addMetric(wavefrontSender, id, null, System.currentTimeMillis(), 1d);
        verify(wavefrontSender, times(1)).sendMetric("name", 1d, time, "host", Collections.emptyMap());
        verifyNoMoreInteractions(wavefrontSender);
    }

    @Test
    void addMetricWhenNanOrInfinityShouldNotAdd() {
        Meter.Id id = registry.counter("name").getId();
        registry.addMetric(wavefrontSender, id, null, System.currentTimeMillis(), Double.NaN);
        registry.addMetric(wavefrontSender, id, null, System.currentTimeMillis(), Double.POSITIVE_INFINITY);
        verifyNoInteractions(wavefrontSender);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        registry.writeMeter(meter);
        verifyNoInteractions(wavefrontSender);
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() throws IOException {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4, measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        registry.writeMeter(meter);
        verify(wavefrontSender, times(1)).sendMetric("my.meter", 1d, clock.wallTime(), "host", Map.of("statistic", "value"));
        verify(wavefrontSender, times(1)).sendMetric("my.meter", 2d, clock.wallTime(), "host", Map.of("statistic", "value"));
        verifyNoMoreInteractions(wavefrontSender);
    }

    @Test
    void addDistribution() throws IOException {
        Meter.Id id = registry.summary("name").getId();
        long time = System.currentTimeMillis();
        List<Pair<Double, Integer>> centroids = Arrays.asList(new Pair<>(1d, 1));
        List<WavefrontHistogramImpl.Distribution> distributions = Arrays.asList(
            new WavefrontHistogramImpl.Distribution(time, centroids)
        );
        registry.addDistribution(wavefrontSender, id, distributions);
        verify(wavefrontSender, times(1)).sendDistribution("name", centroids,
                Set.of(HistogramGranularity.MINUTE), time, "host", Collections.emptyMap());
        verifyNoMoreInteractions(wavefrontSender);
    }
}
