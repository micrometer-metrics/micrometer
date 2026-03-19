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
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StaticTagMetricsFilter}.
 *
 * @author Harsh Verma
 * @since 1.16.5
 */
class StaticTagMetricsFilterTest {

    private MeterRegistry registry;

    private StaticTagMetricsFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        filter = new StaticTagMetricsFilter(registry, Collections.singletonList(Tag.of("application", "testApp")));
    }

    @Test
    void allLevelsCanBeIncrementedIndependently() {
        filter.filter(createLogEvent(Level.FATAL));
        filter.filter(createLogEvent(Level.ERROR));
        filter.filter(createLogEvent(Level.WARN));
        filter.filter(createLogEvent(Level.INFO));
        filter.filter(createLogEvent(Level.DEBUG));
        filter.filter(createLogEvent(Level.TRACE));

        assertThat(registry.get("log4j2.events").tags("level", "fatal").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "warn").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "debug").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "trace").counter().count()).isEqualTo(1.0);
    }

    @Test
    void multipleEventsIncrementCountersCorrectly() {
        filter.filter(createLogEvent(Level.ERROR));
        filter.filter(createLogEvent(Level.ERROR));
        filter.filter(createLogEvent(Level.WARN));
        filter.filter(createLogEvent(Level.INFO));

        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("log4j2.events").tags("level", "warn").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "debug").counter().count()).isEqualTo(0.0);
    }

    @Test
    void customTagsAreApplied() {
        filter.filter(createLogEvent(Level.INFO));

        Counter counter = registry.get("log4j2.events").tags("level", "info").tags("application", "testApp").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    private LogEvent createLogEvent(Level level) {
        return Log4jLogEvent.newBuilder()
            .setLevel(level)
            .setMessage(new SimpleMessage("Test message"))
            .setLoggerName("test.logger")
            .build();
    }

}
