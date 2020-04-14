/**
 * Copyright 2017 VMware, Inc.
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
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
    };

    private final MockClock clock = new MockClock();
    private final WavefrontMeterRegistry registry = new WavefrontMeterRegistry(config, clock);

    @Test
    void addMetric() {
        Stream.Builder<WavefrontMetricLineData> metricsStreamBuilder = Stream.builder();
        Meter.Id id = registry.counter("name").getId();
        registry.addMetric(metricsStreamBuilder, id, null, System.currentTimeMillis(), 1d);
        assertThat(metricsStreamBuilder.build().count()).isEqualTo(1);
    }

    @Test
    void addMetricWhenNanOrInfinityShouldNotAdd() {
        Stream.Builder<WavefrontMetricLineData> metricsStreamBuilder = Stream.builder();
        Meter.Id id = registry.counter("name").getId();
        registry.addMetric(metricsStreamBuilder, id, null, System.currentTimeMillis(), Double.NaN);
        registry.addMetric(metricsStreamBuilder, id, null, System.currentTimeMillis(), Double.POSITIVE_INFINITY);
        assertThat(metricsStreamBuilder.build().count()).isEqualTo(0);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
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
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).hasSize(2);
    }

    @Test
    void addDistribution() {
        Stream.Builder<WavefrontMetricLineData> metricsStreamBuilder = Stream.builder();
        Meter.Id id = registry.summary("name").getId();
        List<Pair<Double, Integer>> centroids = Arrays.asList(new Pair<>(1d, 1));
        List<WavefrontHistogramImpl.Distribution> distributions = Arrays.asList(
            new WavefrontHistogramImpl.Distribution(System.currentTimeMillis(), centroids)
        );
        registry.addDistribution(metricsStreamBuilder, id, distributions);
        assertThat(metricsStreamBuilder.build().count()).isEqualTo(1);
    }
}
