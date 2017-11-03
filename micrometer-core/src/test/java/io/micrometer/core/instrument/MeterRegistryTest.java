package io.micrometer.core.instrument;

import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeterRegistryTest {
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void acceptMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return id.getName().contains("jvm") ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        });

        assertThat(registry.counter("jvm.my.counter")).isInstanceOf(NoopCounter.class);
        assertThat(registry.counter("my.counter")).isNotInstanceOf(NoopCounter.class);
    }

    @Test
    void idTransformingMeterFilter() {
        registry.config().meterFilter(MeterFilter.ignoreTags("k1"));

        registry.counter("my.counter", "k1", "v1");
        assertThat(registry.find("my.counter").counter()).isPresent();
        assertThat(registry.find("my.counter").tags("k1", "v1").counter()).isNotPresent();
    }

    @Test
    void histogramConfigTransformingMeterFilter() {
        MeterRegistry registry = new SimpleMeterRegistry() {
            @Override
            protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig) {
                assertThat(histogramConfig.isPublishingHistogram()).isTrue();
                return super.newTimer(id, histogramConfig);
            }
        };

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public HistogramConfig configure(HistogramConfig config) {
                return HistogramConfig.builder()
                    .percentilesHistogram(true)
                    .build()
                    .merge(config);
            }
        });
        
        registry.timer("my.timer");
    }
}