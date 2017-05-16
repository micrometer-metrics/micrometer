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
