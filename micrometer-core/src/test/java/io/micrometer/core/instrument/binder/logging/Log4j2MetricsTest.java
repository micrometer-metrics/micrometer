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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Log4j2Metrics}.
 *
 * @author Steven Sheehy
 * @author Johnny Lim
 */
class Log4j2MetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private final Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);
    private final Logger additivityDisabledLogger = LogManager.getLogger("additivityDisabledLogger");
    
    private Log4j2Metrics log4j2Metrics;

    @BeforeEach
    void setUp() {
        configureAdditivityDisabledLogger();
        log4j2Metrics = new Log4j2Metrics();
        log4j2Metrics.bindTo(registry);
    }
    
    @AfterEach
    void tearDown() {
        log4j2Metrics.close();
    }

    private void configureAdditivityDisabledLogger() {
        Configurator.setLevel("additivityDisabledLogger", Level.INFO);

        LoggerContext loggerContext = (LoggerContext) LogManager.getContext();
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig loggerConfig = configuration.getLoggerConfig("additivityDisabledLogger");
        loggerConfig.setAdditive(false);
    }

    @Test
    void log4j2LevelMetrics() {
        assertThat(registry.get("log4j2.events").counter().count()).isEqualTo(0.0);

        Configurator.setLevel(Log4j2MetricsTest.class.getName(), Level.INFO);
        logger.info("info");
        logger.warn("warn");
        logger.fatal("fatal");
        logger.error("error");
        logger.debug("debug"); // shouldn't record a metric as per log level config
        logger.trace("trace"); // shouldn't record a metric as per log level config

        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "warn").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "fatal").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("log4j2.events").tags("level", "debug").counter().count()).isEqualTo(0.0);
        assertThat(registry.get("log4j2.events").tags("level", "trace").counter().count()).isEqualTo(0.0);
    }

    @Test
    void filterWhenLoggerAdditivityIsFalseShouldWork() {
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(0);

        additivityDisabledLogger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);
    }

    @Test
    void isLevelEnabledDoesntContributeToCounts() {
        logger.isErrorEnabled();

        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(0.0);
    }

    @Test
    void removeFilterFromLoggerContextOnClose() throws Exception {
        LoggerContext loggerContext = new LoggerContext("test");
        Log4j2Metrics log4j2Metrics = new Log4j2Metrics(emptyList(), loggerContext);
        log4j2Metrics.bindTo(registry);

        LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        assertThat(loggerConfig.getFilter()).isNotNull();
        log4j2Metrics.close();
        assertThat(loggerConfig.getFilter()).isNull();
    }
}
