package io.micrometer.core.instrument.graphite;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.PickledGraphite;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {

    private final GraphiteReporter reporter;
    private final GraphiteConfig config;

    public GraphiteMeterRegistry() {
        this(System::getProperty);
    }

    public GraphiteMeterRegistry(GraphiteConfig config) {
        this(config, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public GraphiteMeterRegistry(GraphiteConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        super(nameMapper, clock);

        this.config = config;

        final PickledGraphite pickledGraphite = new PickledGraphite(new InetSocketAddress(config.host(), config.port()));
        this.reporter = GraphiteReporter.forRegistry(getDropwizardRegistry())
                .convertRatesTo(config.rateUnits())
                .convertDurationsTo(config.durationUnits())
                .build(pickledGraphite);
        start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
    }
}
