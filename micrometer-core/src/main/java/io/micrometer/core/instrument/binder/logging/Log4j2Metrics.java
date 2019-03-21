/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for Apache Log4j 2.
 *
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

    @Nullable
    private MetricsFilter metricsFilter;

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
        metricsFilter = new MetricsFilter(registry, tags);
        metricsFilter.start();

        Configuration configuration = loggerContext.getConfiguration();
        configuration.getRootLogger().addFilter(metricsFilter);
        loggerContext.getLoggers().stream()
            .filter(logger -> !logger.isAdditive())
            .forEach(logger -> configuration.getLoggerConfig(logger.getName()).addFilter(metricsFilter));
        loggerContext.updateLoggers(configuration);
    }

    @Override
    public void close() {
        if (metricsFilter != null) {
            Configuration configuration = loggerContext.getConfiguration();
            configuration.getRootLogger().removeFilter(metricsFilter);
            loggerContext.getLoggers().stream()
                .filter(logger -> !logger.isAdditive())
                .forEach(logger -> configuration.getLoggerConfig(logger.getName()).removeFilter(metricsFilter));
            loggerContext.updateLoggers(configuration);
            metricsFilter.stop();
        }
    }

    @NonNullApi
    @NonNullFields
    class MetricsFilter extends AbstractFilter {

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
                    .baseUnit("events")
                    .register(registry);

            errorCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "error")
                    .description("Number of error level log events")
                    .baseUnit("events")
                    .register(registry);

            warnCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "warn")
                    .description("Number of warn level log events")
                    .baseUnit("events")
                    .register(registry);

            infoCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "info")
                    .description("Number of info level log events")
                    .baseUnit("events")
                    .register(registry);

            debugCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "debug")
                    .description("Number of debug level log events")
                    .baseUnit("events")
                    .register(registry);

            traceCounter = Counter.builder(METER_NAME)
                    .tags(tags)
                    .tags("level", "trace")
                    .description("Number of trace level log events")
                    .baseUnit("events")
                    .register(registry);
        }

        @Override
        public Result filter(LogEvent event) {
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

            return Result.NEUTRAL;
        }
    }
}

