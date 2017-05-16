package org.springframework.metrics.instrument.binder;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.metrics.instrument.MeterRegistry;

public class LogbackMetrics implements MeterBinder {
    @Override
    public void bindTo(MeterRegistry registry) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.addTurboFilter(new MetricsTurboFilter(registry));
    }
}
