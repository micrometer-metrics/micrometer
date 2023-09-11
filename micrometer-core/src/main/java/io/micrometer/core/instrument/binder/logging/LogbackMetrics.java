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
package io.micrometer.core.instrument.binder.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * Metrics instrumentation of Logback log events. Counts the log events with a log level
 * equal to or higher than the corresponding logger's effective log level. A filter added
 * to an appender will not affect the metrics.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class LogbackMetrics implements MeterBinder, AutoCloseable {

    static ThreadLocal<Boolean> ignoreMetrics = new ThreadLocal<>();

    private final Iterable<Tag> tags;

    private final LoggerContext loggerContext;

    private final Map<MeterRegistry, MetricsTurboFilter> metricsTurboFilters = new HashMap<>();

    static {
        // see gh-2868. Without this called statically, the same call in the constructor
        // may return SubstituteLoggerFactory and fail to cast.
        LoggerFactory.getILoggerFactory();
    }

    public LogbackMetrics() {
        this(emptyList());
    }

    public LogbackMetrics(Iterable<Tag> tags) {
        this(tags, (LoggerContext) LoggerFactory.getILoggerFactory());
    }

    public LogbackMetrics(Iterable<Tag> tags, LoggerContext context) {
        this.tags = tags;
        this.loggerContext = context;

        loggerContext.addListener(new LoggerContextListener() {
            @Override
            public boolean isResetResistant() {
                return true;
            }

            @Override
            public void onReset(LoggerContext context) {
                // re-add turbo filter because reset clears the turbo filter list
                synchronized (metricsTurboFilters) {
                    for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
                        loggerContext.addTurboFilter(metricsTurboFilter);
                    }
                }
            }

            @Override
            public void onStart(LoggerContext context) {
                // no-op
            }

            @Override
            public void onStop(LoggerContext context) {
                // no-op
            }

            @Override
            public void onLevelChange(Logger logger, Level level) {
                // no-op
            }
        });
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        MetricsTurboFilter filter = new MetricsTurboFilter(registry, tags);
        synchronized (metricsTurboFilters) {
            metricsTurboFilters.put(registry, filter);
            loggerContext.addTurboFilter(filter);
        }
    }

    /**
     * Used by {@link Counter#increment()} implementations that may cause a logback
     * logging event to occur. Attempting to instrument that implementation would cause a
     * {@link StackOverflowError}.
     * @param r Don't record metrics on logging statements that occur inside of this
     * runnable.
     */
    public static void ignoreMetrics(Runnable r) {
        ignoreMetrics.set(true);
        try {
            r.run();
        }
        finally {
            ignoreMetrics.remove();
        }
    }

    @Override
    public void close() {
        synchronized (metricsTurboFilters) {
            for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
                loggerContext.getTurboFilterList().remove(metricsTurboFilter);
            }
        }
    }

}

@NonNullApi
@NonNullFields
class MetricsTurboFilter extends TurboFilter {

    private static final String METER_NAME = "logback.events";

    private static final String METER_DESCRIPTION = "Number of log events that were enabled by the effective log level";

    private final Counter errorCounter;

    private final Counter warnCounter;

    private final Counter infoCounter;

    private final Counter debugCounter;

    private final Counter traceCounter;

    MetricsTurboFilter(MeterRegistry registry, Iterable<Tag> tags) {
        errorCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "error")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);

        warnCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "warn")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);

        infoCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "info")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);

        debugCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "debug")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);

        traceCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "trace")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        // When filter is asked for decision for an isDebugEnabled call or similar test,
        // there is no message (ie format)
        // and no intention to log anything with this call. We will not increment counters
        // and can return immediately and
        // avoid the relatively expensive ThreadLocal access below. See also logbacks
        // Logger.callTurboFilters().
        // Calling logger.isEnabledFor(level) might be sub-optimal since it calls this
        // filter again. This behavior caused a StackOverflowError in the past.
        if (format == null || !level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }

        Boolean ignored = LogbackMetrics.ignoreMetrics.get();
        if (ignored != null && ignored) {
            return FilterReply.NEUTRAL;
        }

        LogbackMetrics.ignoreMetrics(() -> recordMetrics(level));

        return FilterReply.NEUTRAL;
    }

    private void recordMetrics(Level level) {
        switch (level.toInt()) {
            case Level.ERROR_INT:
                errorCounter.increment();
                break;
            case Level.WARN_INT:
                warnCounter.increment();
                break;
            case Level.INFO_INT:
                infoCounter.increment();
                break;
            case Level.DEBUG_INT:
                debugCounter.increment();
                break;
            case Level.TRACE_INT:
                traceCounter.increment();
                break;
        }

    }

}
