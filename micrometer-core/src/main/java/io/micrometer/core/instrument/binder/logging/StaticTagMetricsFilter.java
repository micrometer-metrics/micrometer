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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.apache.logging.log4j.core.LogEvent;

/**
 * @author Harsh Verma
 * @since 1.16.5
 */
public class StaticTagMetricsFilter extends MetricsFilter {

    private final Counter fatalCounter;

    private final Counter errorCounter;

    private final Counter warnCounter;

    private final Counter infoCounter;

    private final Counter debugCounter;

    private final Counter traceCounter;

    StaticTagMetricsFilter(MeterRegistry registry, Iterable<Tag> tags) {
        super();
        fatalCounter = Counter.builder(METER_NAME)
            .tags(tags)
            .tags("level", "fatal")
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry);

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
    protected void incrementCounter(LogEvent event) {
        switch (event.getLevel().getStandardLevel()) {
            case FATAL:
                fatalCounter.increment();
                break;
            case ERROR:
                errorCounter.increment();
                break;
            case WARN:
                warnCounter.increment();
                break;
            case INFO:
                infoCounter.increment();
                break;
            case DEBUG:
                debugCounter.increment();
                break;
            case TRACE:
                traceCounter.increment();
                break;
            default:
                break;
        }
    }

}
