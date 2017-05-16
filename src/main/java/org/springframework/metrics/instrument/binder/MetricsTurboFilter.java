package org.springframework.metrics.instrument.binder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;

public class MetricsTurboFilter extends TurboFilter {
    private final Map<Level, Counter> levelCounters;

    public MetricsTurboFilter(MeterRegistry registry) {
        levelCounters = new HashMap<Level, Counter>() {{
            put(Level.ERROR, registry.counter("logback_events", "level", "error"));
            put(Level.WARN, registry.counter("logback_events", "level", "warn"));
            put(Level.INFO, registry.counter("logback_events", "level", "info"));
            put(Level.DEBUG, registry.counter("logback_events", "level", "debug"));
            put(Level.TRACE, registry.counter("logback_events", "level", "trace"));
        }};
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        Counter counter = levelCounters.get(level);
        if(counter != null)
            counter.increment();
        return FilterReply.ACCEPT;
    }
}
