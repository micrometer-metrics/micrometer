/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.instrument;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.innoq.junit.jupiter.logging.Logging;
import com.innoq.junit.jupiter.logging.LoggingEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for expected logging from {@link MeterRegistry}.
 */
@Logging
class MeterRegistryLoggingTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void meterRegistrationBeforeMeterFilterConfig(LoggingEvents logEvents) {
        registerMetricsAndConfigure();

        assertThat(logEvents.withLevel(Level.DEBUG)).isEmpty();
        assertThat(logEvents.withLevel(Level.WARN)).singleElement()
            .extracting(ILoggingEvent::getFormattedMessage, as(InstanceOfAssertFactories.STRING))
            .contains("A MeterFilter is being configured after a Meter has been registered to this registry.")
            .doesNotContain("meterRegistrationBeforeMeterFilterConfig")
            .doesNotContain("\tat ");
    }

    @Test
    void meterRegistrationBeforeMeterFilterConfigWithDebugLogging(LoggingEvents logEvents) {
        Logger logger = (Logger) LoggerFactory.getLogger(SimpleMeterRegistry.class);
        Level priorLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            registerMetricsAndConfigure();

            assertThat(logEvents.withLevel(Level.WARN)).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage, as(InstanceOfAssertFactories.STRING))
                .contains("A MeterFilter is being configured after a Meter has been registered to this registry.")
                .containsPattern(
                        "io.micrometer.core.instrument.MeterRegistryLoggingTest.configureCommonTags\\(MeterRegistryLoggingTest.java:\\d+\\)\n"
                                + "\tat io.micrometer.core.instrument.MeterRegistryLoggingTest.registerMetricsAndConfigure\\(MeterRegistryLoggingTest.java:\\d+\\)\n"
                                + "\tat io.micrometer.core.instrument.MeterRegistryLoggingTest.meterRegistrationBeforeMeterFilterConfigWithDebugLogging\\(MeterRegistryLoggingTest.java:\\d+\\)");
        }
        finally {
            logger.setLevel(priorLevel);
        }
    }

    // intentionally nest method calls to assert on a more realistic stack trace
    void registerMetricsAndConfigure() {
        registry.counter("counter");
        configureCommonTags();
    }

    void configureCommonTags() {
        registry.config().commonTags("common", "tag");
    }

}
