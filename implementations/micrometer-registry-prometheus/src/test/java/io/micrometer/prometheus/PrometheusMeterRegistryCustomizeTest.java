package io.micrometer.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusMeterRegistryCustomizeTest {

    private final CollectorRegistry prometheusRegistry = new CollectorRegistry(true);

    private final MockClock clock = new MockClock();
    
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry,
        clock) {
        
        @Override
        protected String getConventionName(Meter.Id id) {
            return "custom_prefix_" + super.getConventionName(id);
        }
    };

    @DisplayName("registered counter collector name is the same that calculated by PrometheusMeterRegistry")
    @Test
    void customNamedCollectorName() {
        Counter.builder("counter").description("my counter").register(registry);
        assertThat(this.registry.getPrometheusRegistry().metricFamilySamples().nextElement().name).isEqualTo("custom_prefix_counter");
    }
        
}
