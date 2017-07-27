package io.micrometer.core.instrument.graphite;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.PickledGraphite;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {
    public static class Builder {
        private MetricRegistry registry = new MetricRegistry();
        private HierarchicalNameMapper nameMapper = new HierarchicalNameMapper();
        private Clock clock = Clock.SYSTEM;
        private GraphiteConfig config = System::getProperty;

        public Builder nameMapper(HierarchicalNameMapper nameMapper) {
            this.nameMapper = nameMapper;
            return this;
        }

        public Builder config(GraphiteConfig config) {
            this.config = config;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder registry(MetricRegistry registry) {
            this.registry = registry;
            return this;
        }

        public GraphiteMeterRegistry create() {
            return new GraphiteMeterRegistry(registry, nameMapper, clock, config);
        }
    }

    public static Builder build() {
        return new Builder();
    }

    public static GraphiteMeterRegistry local() {
        return build().create();
    }
    
    private GraphiteMeterRegistry(MetricRegistry registry, HierarchicalNameMapper nameMapper, Clock clock, GraphiteConfig config) {
        super(registry, nameMapper, clock);

        final PickledGraphite pickledGraphite = new PickledGraphite(new InetSocketAddress(config.host(), config.port()));
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
                .convertRatesTo(config.rateUnits())
                .convertDurationsTo(config.durationUnits())
                .build(pickledGraphite);
        reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
    }
}
