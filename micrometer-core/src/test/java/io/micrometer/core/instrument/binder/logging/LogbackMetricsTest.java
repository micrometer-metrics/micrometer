/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static io.micrometer.core.instrument.Statistic.Count;
import static org.assertj.core.api.Assertions.assertThat;

class LogbackMetricsTest {
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private Logger logger = (Logger) LoggerFactory.getLogger("foo");

    @BeforeEach
    void bindLogbackMetrics() {
        new LogbackMetrics().bindTo(registry);
    }

    @Test
    void logbackLevelMetrics() {
        assertThat(registry.find("logback.events").value(Count, 0.0).counter()).isPresent();

        logger.setLevel(Level.INFO);

        logger.warn("warn");
        logger.error("error");
        logger.debug("debug"); // shouldn't record a metric

        assertThat(registry.find("logback.events").tags("level", "warn").value(Count, 1.0).counter()).isPresent();
        assertThat(registry.find("logback.events").tags("level", "debug").value(Count, 0.0).counter()).isPresent();
    }

    @Issue("#183")
    @Test
    void isLevelEnabledDoesntContributeToCounts() {
        logger.isErrorEnabled();

        assertThat(registry.find("logback.events").tags("level", "error").value(Count, 0.0).counter()).isPresent();
    }
}
