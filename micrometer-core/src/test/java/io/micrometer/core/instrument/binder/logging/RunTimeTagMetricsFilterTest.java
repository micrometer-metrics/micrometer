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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RunTimeTagMetricsFilter}.
 *
 * @author Harsh Verma
 * @since 1.16.5
 */
class RunTimeTagMetricsFilterTest {

    private MeterRegistry registry;

    private RunTimeTagMetricsFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void incrementsCounterWithStaticTags() {
        filter = new RunTimeTagMetricsFilter(registry, Collections.singletonList(Tag.of("application", "testApp")),
                Collections.emptyList());

        LogEvent event = createLogEvent(Level.INFO);
        filter.filter(event);

        Counter counter = registry.get("log4j2.events").tags("level", "info").tags("application", "testApp").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementsCounterWithDynamicTags() {
        LogEventInterceptor interceptor = (logEvent) -> {
            if (logEvent.getThrown() != null) {
                return Collections.singletonList(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            return Collections.emptyList();
        };

        filter = new RunTimeTagMetricsFilter(registry, Collections.emptyList(), Collections.singletonList(interceptor));

        LogEvent event = createLogEventWithException(Level.INFO, new NullPointerException());
        filter.filter(event);

        Counter counter = registry.get("log4j2.events")
            .tags("level", "info")
            .tags("exception", "NullPointerException")
            .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementsCounterWithBothStaticAndDynamicTags() {
        LogEventInterceptor interceptor = (logEvent) -> {
            if (logEvent.getThrown() != null) {
                return Collections.singletonList(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            return Collections.emptyList();
        };

        filter = new RunTimeTagMetricsFilter(registry, Collections.singletonList(Tag.of("application", "testApp")),
                Collections.singletonList(interceptor));

        LogEvent event = createLogEventWithException(Level.ERROR, new java.io.IOException());
        filter.filter(event);

        Counter counter = registry.get("log4j2.events")
            .tags("level", "error")
            .tags("application", "testApp")
            .tags("exception", "IOException")
            .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void multipleInterceptorsAddMultipleTags() {
        LogEventInterceptor exceptionInterceptor = (logEvent) -> {
            if (logEvent.getThrown() != null) {
                return Collections.singletonList(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            return Collections.emptyList();
        };
        LogEventInterceptor statusInterceptor = (logEvent) -> Collections.singletonList(Tag.of("outcome", "failure"));

        filter = new RunTimeTagMetricsFilter(registry, Collections.emptyList(),
                Arrays.asList(exceptionInterceptor, statusInterceptor));

        LogEvent event = createLogEventWithException(Level.WARN, new java.sql.SQLException());
        filter.filter(event);

        Counter counter = registry.get("log4j2.events")
            .tags("level", "warn")
            .tags("exception", "SQLException")
            .tags("outcome", "failure")
            .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void interceptorReturnsMultipleTags() {
        LogEventInterceptor interceptor = (logEvent) -> {
            List<Tag> tags = new ArrayList<>();
            if (logEvent.getThrown() != null) {
                tags.add(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            tags.add(Tag.of("operation", "database_query"));
            return tags;
        };

        filter = new RunTimeTagMetricsFilter(registry, Collections.emptyList(), Collections.singletonList(interceptor));

        LogEvent event = createLogEventWithException(Level.DEBUG, new java.util.concurrent.TimeoutException());
        filter.filter(event);

        Counter counter = registry.get("log4j2.events")
            .tags("level", "debug")
            .tags("exception", "TimeoutException")
            .tags("operation", "database_query")
            .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void multipleEventsWithDifferentDynamicTags() {
        LogEventInterceptor interceptor = (logEvent) -> {
            if (logEvent.getThrown() != null) {
                return Collections.singletonList(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            return Collections.emptyList();
        };

        filter = new RunTimeTagMetricsFilter(registry, Collections.emptyList(), Collections.singletonList(interceptor));

        filter.filter(createLogEventWithException(Level.INFO, new java.io.IOException()));
        filter.filter(createLogEventWithException(Level.INFO, new java.sql.SQLException()));

        Counter counter1 = registry.get("log4j2.events")
            .tags("level", "info")
            .tags("exception", "IOException")
            .counter();
        Counter counter2 = registry.get("log4j2.events")
            .tags("level", "info")
            .tags("exception", "SQLException")
            .counter();

        assertThat(counter1.count()).isEqualTo(1.0);
        assertThat(counter2.count()).isEqualTo(1.0);
    }

    @Test
    void differentLogLevelsCreateSeparateCounters() {
        filter = new RunTimeTagMetricsFilter(registry, Collections.singletonList(Tag.of("app", "test")),
                Collections.emptyList());

        filter.filter(createLogEvent(Level.INFO));
        filter.filter(createLogEvent(Level.ERROR));
        filter.filter(createLogEvent(Level.WARN));

        assertThat(registry.get("log4j2.events").tags("level", "info").tags("app", "test").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "error").tags("app", "test").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "warn").tags("app", "test").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void meterHasCorrectMetadata() {
        filter = new RunTimeTagMetricsFilter(registry, Collections.emptyList(), Collections.emptyList());

        LogEvent event = createLogEvent(Level.INFO);
        filter.filter(event);

        Meter meter = registry.get("log4j2.events").tags("level", "info").meter();
        assertThat(meter.getId().getBaseUnit()).isEqualTo(BaseUnits.EVENTS);
        assertThat(meter.getId().getDescription()).isEqualTo("Number of log events");
    }

    @Test
    void multipleEventsWithSameTagsIncrementSameCounter() {
        LogEventInterceptor interceptor = (logEvent) -> {
            if (logEvent.getThrown() != null) {
                return Collections.singletonList(Tag.of("exception", logEvent.getThrown().getClass().getSimpleName()));
            }
            return Collections.emptyList();
        };

        filter = new RunTimeTagMetricsFilter(registry, Collections.singletonList(Tag.of("app", "test")),
                Collections.singletonList(interceptor));

        filter.filter(createLogEventWithException(Level.INFO, new NullPointerException()));
        filter.filter(createLogEventWithException(Level.INFO, new NullPointerException()));
        filter.filter(createLogEventWithException(Level.INFO, new NullPointerException()));

        Counter counter = registry.get("log4j2.events")
            .tags("level", "info")
            .tags("app", "test")
            .tags("exception", "NullPointerException")
            .counter();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    private LogEvent createLogEvent(Level level) {
        return Log4jLogEvent.newBuilder()
            .setLevel(level)
            .setMessage(new SimpleMessage("Test message"))
            .setLoggerName("test.logger")
            .build();
    }

    private LogEvent createLogEventWithException(Level level, Throwable throwable) {
        return Log4jLogEvent.newBuilder()
            .setLevel(level)
            .setMessage(new SimpleMessage("Test message"))
            .setLoggerName("test.logger")
            .setThrown(throwable)
            .build();
    }

}
