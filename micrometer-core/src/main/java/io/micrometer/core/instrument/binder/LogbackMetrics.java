/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import io.micrometer.core.instrument.Counter;

public class LogbackMetrics implements MeterBinder {
    @Override
    public void bindTo(MeterRegistry registry) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.addTurboFilter(new MetricsTurboFilter(registry));
    }
}

class MetricsTurboFilter extends TurboFilter {
    private final Counter errorCounter;
    private final Counter warnCounter;
    private final Counter infoCounter;
    private final Counter debugCounter;
    private final Counter traceCounter;

    MetricsTurboFilter(MeterRegistry registry) {
        errorCounter = registry.meter("logback_events").tag("level", "error").counter();
        warnCounter = registry.meter("logback_events").tag("level", "warn").counter();
        infoCounter = registry.meter("logback_events").tag("level", "info").counter();
        debugCounter = registry.meter("logback_events").tag("level", "debug").counter();
        traceCounter = registry.meter("logback_events").tag("level", "trace").counter();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        switch(level.toInt()) {
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

        return FilterReply.NEUTRAL;
    }
}
