/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.util.internal.logging;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.INFO;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE;
import static io.micrometer.core.util.internal.logging.InternalLogLevel.WARN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonatan Ivanov
 */
class MockLoggerTest {
    private static final MockLogger LOGGER = new MockLoggerFactory().getLogger("testLogger");

    @AfterEach
    void tearDown() {
        LOGGER.clear();
    }

    @Test
    void shouldHaveTheRightName() {
        assertThat(LOGGER.name()).isEqualTo("testLogger");
    }

    @Test
    void shouldBeEmptyIfNeverUsed() {
        assertThat(LOGGER.getLogEvents()).isEmpty();
    }

    @Test
    void shouldBeEmptyAfterClear() {
        LOGGER.info("test event");
        LOGGER.error(new IOException("simulated"));
        assertThat(LOGGER.getLogEvents()).hasSize(2);
        LOGGER.clear();
        assertThat(LOGGER.getLogEvents()).isEmpty();
    }

    @Test
    void everyLevelShouldBeEnabled() {
        assertThat(LOGGER.isTraceEnabled()).isTrue();
        assertThat(LOGGER.isDebugEnabled()).isTrue();
        assertThat(LOGGER.isInfoEnabled()).isTrue();
        assertThat(LOGGER.isWarnEnabled()).isTrue();
        assertThat(LOGGER.isErrorEnabled()).isTrue();

        assertThat(LOGGER.isEnabled(TRACE)).isTrue();
        assertThat(LOGGER.isEnabled(DEBUG)).isTrue();
        assertThat(LOGGER.isEnabled(INFO)).isTrue();
        assertThat(LOGGER.isEnabled(WARN)).isTrue();
        assertThat(LOGGER.isEnabled(ERROR)).isTrue();
    }

    @Test
    void shouldContainTheRightEventsOnTraceLevel() {
        Throwable cause = new IOException("simulated");

        LOGGER.trace("test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.trace("test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.trace("test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.trace("test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.trace("test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.trace(cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(TRACE, cause);
    }

    @Test
    void shouldContainTheRightEventsOnDebugLevel() {
        Throwable cause = new IOException("simulated");

        LOGGER.debug("test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.debug("test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.debug("test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.debug("test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.debug("test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.debug(cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(DEBUG, cause);
    }

    @Test
    void shouldContainTheRightEventsOnInfoLevel() {
        Throwable cause = new IOException("simulated");

        LOGGER.info("test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.info("test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.info("test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.info("test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.info("test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.info(cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(INFO, cause);
    }

    @Test
    void shouldContainTheRightEventsOnWarnLevel() {
        Throwable cause = new IOException("simulated");

        LOGGER.warn("test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.warn("test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.warn("test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.warn("test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.warn("test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.warn(cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(WARN, cause);
    }

    @Test
    void shouldContainTheRightEventsOnErrorLevel() {
        Throwable cause = new IOException("simulated");

        LOGGER.error("test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.error("test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.error("test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.error("test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.error("test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.error(cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(ERROR, cause);
    }

    @Test
    void shouldContainTheRightEvents() {
        Throwable cause = new IOException("simulated");

        LOGGER.log(INFO, "test event 01");
        assertThat(LOGGER.getLogEvents()).hasSize(1);

        LOGGER.log(INFO, "test event {}", "02");
        assertThat(LOGGER.getLogEvents()).hasSize(2);

        LOGGER.log(INFO, "test {} {}", "event", "03");
        assertThat(LOGGER.getLogEvents()).hasSize(3);

        LOGGER.log(INFO, "test {} {} {}", "event", "04", "with varargs");
        assertThat(LOGGER.getLogEvents()).hasSize(4);

        LOGGER.log(INFO, "test event 05", cause);
        assertThat(LOGGER.getLogEvents()).hasSize(5);

        LOGGER.log(INFO, cause);
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        verifyStandardEvents(INFO, cause);
    }

    private void verifyStandardEvents(InternalLogLevel level, Throwable cause) {
        assertThat(LOGGER.getLogEvents()).hasSize(6);

        assertThat(LOGGER.getLogEvents().get(0).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(0).getMessage()).isEqualTo("test event 01");
        assertThat(LOGGER.getLogEvents().get(0).getCause()).isNull();

        assertThat(LOGGER.getLogEvents().get(1).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(1).getMessage()).isEqualTo("test event 02");
        assertThat(LOGGER.getLogEvents().get(1).getCause()).isNull();

        assertThat(LOGGER.getLogEvents().get(2).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(2).getMessage()).isEqualTo("test event 03");
        assertThat(LOGGER.getLogEvents().get(2).getCause()).isNull();

        assertThat(LOGGER.getLogEvents().get(3).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(3).getMessage()).isEqualTo("test event 04 with varargs");
        assertThat(LOGGER.getLogEvents().get(3).getCause()).isNull();

        assertThat(LOGGER.getLogEvents().get(4).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(4).getMessage()).isEqualTo("test event 05");
        assertThat(LOGGER.getLogEvents().get(4).getCause()).isSameAs(cause);

        assertThat(LOGGER.getLogEvents().get(5).getLevel()).isSameAs(level);
        assertThat(LOGGER.getLogEvents().get(5).getMessage()).isNull();
        assertThat(LOGGER.getLogEvents().get(5).getCause()).isSameAs(cause);
    }
}
