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
import java.util.ArrayList;
import java.util.List;

/**
 * @author Harsh Verma
 * @since 1.16.5
 */
public class RunTimeTagMetricsFilter extends MetricsFilter {

    private final Iterable<LogEventInterceptor> logEventInterceptors;

    private final List<Tag> tagsForLogEvent;

    private final MeterRegistry registry;

    RunTimeTagMetricsFilter(MeterRegistry registry, Iterable<Tag> tags,
            Iterable<LogEventInterceptor> logEventInterceptors) {
        super();
        this.tagsForLogEvent = new ArrayList<>();
        for (Tag tag : tags) {
            tagsForLogEvent.add(tag);
        }
        this.logEventInterceptors = logEventInterceptors;
        this.registry = registry;
    }

    @Override
    protected void incrementCounter(LogEvent event) {
        ArrayList<Tag> tagsForLogEvent = new ArrayList<>(this.tagsForLogEvent);
        for (LogEventInterceptor logEventInterceptor : logEventInterceptors) {
            tagsForLogEvent.addAll(logEventInterceptor.getTagsForLogEvent(event));
        }
        Counter.builder(METER_NAME)
            .tags(tagsForLogEvent)
            .tags("level", event.getLevel().getStandardLevel().name().toLowerCase())
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.EVENTS)
            .register(registry)
            .increment();
    }

}
