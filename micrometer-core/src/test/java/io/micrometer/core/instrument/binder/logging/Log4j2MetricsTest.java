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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link Log4j2Metrics}.
 *
 * @author Steven Sheehy
 * @author Johnny Lim
 */
class Log4j2MetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    @BeforeEach
    void cleanUp() {
        LogManager.shutdown();
    }

    @AfterAll
    static void cleanUpAfterAll() {
        LogManager.shutdown();
    }

    @Test
    void log4j2LevelMetrics() {
        // tag::setup[]
        new Log4j2Metrics().bindTo(registry);
        // end::setup[]

        assertThat(registry.get("log4j2.events").counter().count()).isEqualTo(0.0);

        // tag::example[]
        Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);
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
        // end::example[]
    }

    @Test
    void filterWhenLoggerAdditivityIsFalseShouldWork() {
        Logger additivityDisabledLogger = LogManager.getLogger("additivityDisabledLogger");
        Configurator.setLevel("additivityDisabledLogger", Level.INFO);

        LoggerContext loggerContext = (LoggerContext) LogManager.getContext();
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig loggerConfig = configuration.getLoggerConfig("additivityDisabledLogger");
        loggerConfig.setAdditive(false);

        new Log4j2Metrics().bindTo(registry);

        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(0);

        additivityDisabledLogger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);
    }

    @Issue("#1466")
    @Test
    void filterWhenRootLoggerAdditivityIsFalseShouldWork() throws IOException {
        ConfigurationSource source = new ConfigurationSource(
                getClass().getResourceAsStream("/binder/logging/log4j2-root-logger-additivity-false.xml"));
        Configurator.initialize(null, source);

        Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);

        new Log4j2Metrics().bindTo(registry);

        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(0);

        logger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);
    }

    @Test
    void isLevelEnabledDoesntContributeToCounts() {
        new Log4j2Metrics().bindTo(registry);

        Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);
        logger.isErrorEnabled();

        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(0.0);
    }

    @Test
    void removeFilterFromLoggerContextOnClose() {
        new Log4j2Metrics().bindTo(registry);

        LoggerContext loggerContext = new LoggerContext("test");
        Log4j2Metrics log4j2Metrics = new Log4j2Metrics(emptyList(), loggerContext);
        log4j2Metrics.bindTo(registry);

        LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        assertThat(loggerConfig.getFilter()).isNotNull();
        log4j2Metrics.close();
        assertThat(loggerConfig.getFilter()).isNull();
    }

    @Test
    void noDuplicateLoggingCountWhenMultipleNonAdditiveLoggersShareConfig() {
        LoggerContext loggerContext = new LoggerContext("test");

        LoggerConfig loggerConfig = new LoggerConfig("com.test", Level.INFO, false);
        Configuration configuration = loggerContext.getConfiguration();
        configuration.addLogger("com.test", loggerConfig);
        loggerContext.setConfiguration(configuration);
        loggerContext.updateLoggers();

        Logger logger1 = loggerContext.getLogger("com.test.log1");
        loggerContext.getLogger("com.test.log2");

        new Log4j2Metrics(emptyList(), loggerContext).bindTo(registry);

        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(0);
        logger1.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);
    }

    @Issue("#2176")
    @Test
    void asyncLogShouldNotBeDuplicated() throws IOException {
        ConfigurationSource source = new ConfigurationSource(
                getClass().getResourceAsStream("/binder/logging/log4j2-async-logger.xml"));
        Configurator.initialize(null, source);

        Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);

        new Log4j2Metrics().bindTo(registry);

        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(0);
        logger.info("Hello, world!");
        logger.info("Hello, world!");
        logger.info("Hello, world!");
        await().atMost(Duration.ofSeconds(1))
            .until(() -> registry.get("log4j2.events").tags("level", "info").counter().count() == 3);
    }

    // see https://github.com/micrometer-metrics/micrometer/pull/872
    @Test
    void shouldTriggerLoggersUpdateOnOpenAndClose() {
        LoggerContext context = new LoggerContext("test");

        AtomicInteger reconfigureCount = new AtomicInteger();
        context.addPropertyChangeListener(event -> {
            if (event.getNewValue() instanceof Configuration) {
                reconfigureCount.incrementAndGet();
            }
        });

        Log4j2Metrics metrics = new Log4j2Metrics(emptyList(), context);

        assertThat(reconfigureCount.get()).isEqualTo(0);
        metrics.bindTo(registry);
        assertThat(reconfigureCount.get()).isEqualTo(1);
        metrics.close();
        assertThat(reconfigureCount.get()).isEqualTo(2);
    }

    @Test
    void metricsFilterIsReused() {
        LoggerContext loggerContext = new LoggerContext("test");

        LoggerConfig loggerConfig = new LoggerConfig("com.test", Level.INFO, false);
        Configuration configuration = loggerContext.getConfiguration();
        configuration.addLogger("com.test", loggerConfig);
        loggerContext.setConfiguration(configuration);
        loggerContext.updateLoggers();

        Logger logger1 = loggerContext.getLogger("com.test.log1");
        loggerContext.getLogger("com.test.log2");

        new Log4j2Metrics(emptyList(), loggerContext).bindTo(registry);
        Iterator<Filter> rootFilters = loggerContext.getRootLogger().getFilters();
        Log4j2Metrics.MetricsFilter rootFilter = (Log4j2Metrics.MetricsFilter) rootFilters.next();
        assertThat(rootFilters.hasNext()).isFalse();

        Log4j2Metrics.MetricsFilter logger1Filter = (Log4j2Metrics.MetricsFilter) loggerContext.getConfiguration()
            .getLoggerConfig(logger1.getName())
            .getFilter();
        assertThat(logger1Filter).isEqualTo(rootFilter);
    }

    @Test
    void multipleRegistriesCanBeBound() {
        MeterRegistry registry2 = new SimpleMeterRegistry();

        Log4j2Metrics log4j2Metrics = new Log4j2Metrics(emptyList());
        log4j2Metrics.bindTo(registry);

        Logger logger = LogManager.getLogger(Log4j2MetricsTest.class);
        Configurator.setLevel(logger, Level.INFO);
        logger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);

        log4j2Metrics.bindTo(registry2);
        logger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(2);
        assertThat(registry2.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);

    }

    @Test
    void multipleRegistriesCanBeBoundWithNonRootLoggerContext() {
        LoggerContext loggerContext = new LoggerContext("test");

        LoggerConfig loggerConfig = new LoggerConfig("com.test", Level.INFO, false);
        Configuration configuration = loggerContext.getConfiguration();
        configuration.getRootLogger().setLevel(Level.INFO);
        configuration.addLogger("com.test", loggerConfig);
        loggerContext.updateLoggers(configuration);

        MeterRegistry registry2 = new SimpleMeterRegistry();

        Log4j2Metrics log4j2Metrics = new Log4j2Metrics(emptyList(), loggerContext);
        log4j2Metrics.bindTo(registry);

        // verify root logger
        Logger logger = loggerContext.getLogger(Log4j2MetricsTest.class);
        logger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);

        // verify other logger
        Logger logger2 = loggerContext.getLogger("com.test");
        logger2.info("Using other logger than root logger");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(2);

        log4j2Metrics.bindTo(registry2);

        logger.info("Hello, world!");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(3);
        assertThat(registry2.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(1);

        logger2.info("Using other logger than root logger");
        assertThat(registry.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(4);
        // this final check does not pass as the log event is not properly counted
        assertThat(registry2.get("log4j2.events").tags("level", "info").counter().count()).isEqualTo(2);
    }

    @Issue("#5756")
    @Test
    void rebindsMetricsWhenConfigurationIsReloaded() {
        LoggerContext context = new LoggerContext("test");
        Logger logger = context.getLogger("com.test");
        Configuration oldConfiguration = context.getConfiguration();

        try (Log4j2Metrics metrics = new Log4j2Metrics(emptyList(), context)) {
            metrics.bindTo(registry);

            logger.error("first");
            assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1);

            // Should have added filter to configuration
            Filter oldFilter = oldConfiguration.getRootLogger().getFilter();
            assertThat(oldFilter).isInstanceOf(Log4j2Metrics.MetricsFilter.class);

            // This will reload the configuration to default
            context.reconfigure();

            Configuration newConfiguration = context.getConfiguration();

            // For this event to be counted, the metrics must be rebound
            logger.error("second");
            assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(2);

            // Should have removed filter from old configuration, adding it to the new
            assertThat(oldConfiguration.getRootLogger().getFilter()).isNull();
            Filter newFilter = newConfiguration.getRootLogger().getFilter();
            assertThat(newFilter).isInstanceOf(Log4j2Metrics.MetricsFilter.class);
        }
    }

    @Test
    void shouldNotRebindMetricsIfBinderIsClosed() {
        LoggerContext context = new LoggerContext("test");
        Logger logger = context.getLogger("com.test");

        try (Log4j2Metrics metrics = new Log4j2Metrics(emptyList(), context)) {
            metrics.bindTo(registry);
            logger.error("first");
            assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1);
        }

        // This will reload the configuration to default
        context.reconfigure();

        // This event should not be counted as the metrics binder is already closed
        logger.error("second");

        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1);
    }

    @Test
    void bindingTwiceToSameRegistry_doesNotDoubleCount() {
        LoggerContext context = new LoggerContext("test");
        Logger logger = context.getLogger("com.test");

        try (Log4j2Metrics metrics = new Log4j2Metrics(emptyList(), context)) {
            // binding twice
            metrics.bindTo(registry);
            metrics.bindTo(registry);

            logger.error("first");

            assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(1);

            context.reconfigure();

            logger.error("second");
            assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(2);
        }

        // no additional events should be counted now
        context.reconfigure();

        logger.error("third");
        assertThat(registry.get("log4j2.events").tags("level", "error").counter().count()).isEqualTo(2);
    }

}
