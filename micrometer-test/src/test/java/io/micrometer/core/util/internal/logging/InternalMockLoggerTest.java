/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.util.internal.logging;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
class InternalMockLoggerTest {
    private static InternalMockLogger logger;

    @BeforeAll
    static void setUpClass() {
        InternalLoggerFactory.setDefaultFactory(new InternalMockLoggerFactory());
        logger = (InternalMockLogger) InternalLoggerFactory.getInstance("testLogger");
    }

    @AfterEach
    void tearDown() {
        logger.clear();
    }

    @Test
    void shouldHaveTheRightName() {
        assertThat(logger.name()).isEqualTo("testLogger");
    }

    @Test
    void shouldBeEmptyIfNeverUsed() {
        assertThat(logger.getLogEvents()).isEmpty();
    }

    @Test
    void shouldBeEmptyAfterClear() {
        logger.info("test event");
        logger.error(new IOException("simulated"));
        logger.clear();
        assertThat(logger.getLogEvents()).isEmpty();
    }

    @Test
    void everyLevelShouldBeEnabled() {
        assertThat(logger.isTraceEnabled()).isTrue();
        assertThat(logger.isDebugEnabled()).isTrue();
        assertThat(logger.isInfoEnabled()).isTrue();
        assertThat(logger.isWarnEnabled()).isTrue();
        assertThat(logger.isErrorEnabled()).isTrue();

        assertThat(logger.isEnabled(TRACE)).isTrue();
        assertThat(logger.isEnabled(DEBUG)).isTrue();
        assertThat(logger.isEnabled(INFO)).isTrue();
        assertThat(logger.isEnabled(WARN)).isTrue();
        assertThat(logger.isEnabled(ERROR)).isTrue();
    }

    @Test
    void shouldContainTheRightEventsOnTraceLevel() {
        Throwable cause = new IOException("simulated");

        logger.trace("test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.trace("test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.trace("test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.trace("test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.trace("test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.trace(cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(TRACE, cause);
    }

    @Test
    void shouldContainTheRightEventsOnDebugLevel() {
        Throwable cause = new IOException("simulated");

        logger.debug("test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.debug("test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.debug("test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.debug("test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.debug("test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.debug(cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(DEBUG, cause);
    }

    @Test
    void shouldContainTheRightEventsOnInfoLevel() {
        Throwable cause = new IOException("simulated");

        logger.info("test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.info("test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.info("test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.info("test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.info("test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.info(cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(INFO, cause);
    }

    @Test
    void shouldContainTheRightEventsOnWarnLevel() {
        Throwable cause = new IOException("simulated");

        logger.warn("test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.warn("test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.warn("test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.warn("test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.warn("test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.warn(cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(WARN, cause);
    }

    @Test
    void shouldContainTheRightEventsOnErrorLevel() {
        Throwable cause = new IOException("simulated");

        logger.error("test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.error("test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.error("test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.error("test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.error("test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.error(cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(ERROR, cause);
    }

    @Test
    void shouldContainTheRightEvents() {
        Throwable cause = new IOException("simulated");

        logger.log(INFO, "test event 01");
        assertThat(logger.getLogEvents()).hasSize(1);

        logger.log(INFO, "test event {}", "02");
        assertThat(logger.getLogEvents()).hasSize(2);

        logger.log(INFO, "test {} {}", "event", "03");
        assertThat(logger.getLogEvents()).hasSize(3);

        logger.log(INFO, "test {} {} {}", "event", "04", "with varargs");
        assertThat(logger.getLogEvents()).hasSize(4);

        logger.log(INFO, "test event 05", cause);
        assertThat(logger.getLogEvents()).hasSize(5);

        logger.log(INFO, cause);
        assertThat(logger.getLogEvents()).hasSize(6);

        verifyStandardEvents(INFO, cause);
    }

    private void verifyStandardEvents(InternalLogLevel level, Throwable cause) {
        assertThat(logger.getLogEvents()).hasSize(6);

        assertThat(logger.getLogEvents().get(0).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(0).getMessage()).isEqualTo("test event 01");
        assertThat(logger.getLogEvents().get(0).getCause()).isNull();

        assertThat(logger.getLogEvents().get(1).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(1).getMessage()).isEqualTo("test event 02");
        assertThat(logger.getLogEvents().get(1).getCause()).isNull();

        assertThat(logger.getLogEvents().get(2).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(2).getMessage()).isEqualTo("test event 03");
        assertThat(logger.getLogEvents().get(2).getCause()).isNull();

        assertThat(logger.getLogEvents().get(3).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(3).getMessage()).isEqualTo("test event 04 with varargs");
        assertThat(logger.getLogEvents().get(3).getCause()).isNull();

        assertThat(logger.getLogEvents().get(4).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(4).getMessage()).isEqualTo("test event 05");
        assertThat(logger.getLogEvents().get(4).getCause()).isSameAs(cause);

        assertThat(logger.getLogEvents().get(5).getLevel()).isSameAs(level);
        assertThat(logger.getLogEvents().get(5).getMessage()).isNull();
        assertThat(logger.getLogEvents().get(5).getCause()).isSameAs(cause);
    }
}
