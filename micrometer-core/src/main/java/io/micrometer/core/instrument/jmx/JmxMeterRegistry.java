package io.micrometer.core.instrument.jmx;

import com.codahale.metrics.JmxReporter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class JmxMeterRegistry extends DropwizardMeterRegistry {
    private final JmxReporter reporter;

    public JmxMeterRegistry() {
        this(HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public JmxMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock) {
        super(nameMapper, clock);

        this.reporter = JmxReporter.forRegistry(getDropwizardRegistry()).build();
        this.reporter.start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }
}
