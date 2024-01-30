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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for Apache Log4j 2. Please use at least 2.21.0 since there was a
 * bug in earlier versions that prevented Micrometer to increment its counters correctly.
 *
 * @see <a href=
 * "https://github.com/apache/logging-log4j2/issues/1550">logging-log4j2#1550</a>
 * @see <a href=
 * "https://github.com/micrometer-metrics/micrometer/issues/2176">micrometer#2176</a>
 * @author Steven Sheehy
 * @author Johnny Lim
 * @since 1.1.0
 */
@NonNullApi
@NonNullFields
public class Log4j2Metrics implements MeterBinder, AutoCloseable {

    private static final String METER_NAME = "log4j2.events";

    private final Iterable<Tag> tags;

    private final LoggerContext loggerContext;

    private final List<MetricsFilter> metricsFilters = new ArrayList<>();

    public Log4j2Metrics() {
        this(emptyList());
    }

    public Log4j2Metrics(Iterable<Tag> tags) {
        this(tags, (LoggerContext) LogManager.getContext(false));
    }

    public Log4j2Metrics(Iterable<Tag> tags, LoggerContext loggerContext) {
        this.tags = tags;
        this.loggerContext = loggerContext;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig rootLoggerConfig = configuration.getRootLogger();
        rootLoggerConfig.addFilter(createMetricsFilterAndStart(registry));

        loggerContext.getConfiguration()
            .getLoggers()
            .values()
            .stream()
            .filter(loggerConfig -> !loggerConfig.isAdditive())
            .forEach(loggerConfig -> {
                if (loggerConfig == rootLoggerConfig) {
                    return;
                }
                Filter logFilter = loggerConfig.getFilter();

                if (logFilter instanceof CompositeFilter
                        && Arrays.stream(((CompositeFilter) logFilter).getFiltersArray())
                            .anyMatch(innerFilter -> innerFilter instanceof MetricsFilter)) {
                    return;
                }

                if (logFilter instanceof MetricsFilter) {
                    return;
                }
                loggerConfig.addFilter(createMetricsFilterAndStart(registry));
            });

        loggerContext.updateLoggers(configuration);
    }

    private MetricsFilter createMetricsFilterAndStart(MeterRegistry registry) {
        MetricsFilter metricsFilter = new MetricsFilter(registry, tags);
        metricsFilter.start();
        metricsFilters.add(metricsFilter);
        return metricsFilter;
    }

    @Override
    public void close() {
        if (!metricsFilters.isEmpty()) {
            Configuration configuration = loggerContext.getConfiguration();
            LoggerConfig rootLoggerConfig = configuration.getRootLogger();
            metricsFilters.forEach(rootLoggerConfig::removeFilter);

            loggerContext.getConfiguration()
                .getLoggers()
                .values()
                .stream()
                .filter(loggerConfig -> !loggerConfig.isAdditive())
                .forEach(loggerConfig -> {
                    if (loggerConfig != rootLoggerConfig) {
                        metricsFilters.forEach(loggerConfig::removeFilter);
                    }
                });

            loggerContext.updateLoggers(configuration);
            metricsFilters.forEach(MetricsFilter::stop);
        }
    }

    @NonNullApi
    @NonNullFields
    static class MetricsFilter extends AbstractFilter {

        private final Counter fatalCounter;

        private final Counter errorCounter;

        private final Counter warnCounter;

        private final Counter infoCounter;

        private final Counter debugCounter;

        private final Counter traceCounter;

        MetricsFilter(MeterRegistry registry, Iterable<Tag> tags) {
            fatalCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "fatal")
                .description("Number of fatal level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            errorCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "error")
                .description("Number of error level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            warnCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "warn")
                .description("Number of warn level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            infoCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "info")
                .description("Number of info level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            debugCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "debug")
                .description("Number of debug level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);

            traceCounter = Counter.builder(METER_NAME)
                .tags(tags)
                .tags("level", "trace")
                .description("Number of trace level log events")
                .baseUnit(BaseUnits.EVENTS)
                .register(registry);
        }

        @Override
        public Result filter(LogEvent event) {
            incrementCounter(event);
            return Result.NEUTRAL;
        }

        private void incrementCounter(LogEvent event) {
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

}
