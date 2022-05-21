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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Jonatan Ivanov
 */
@Deprecated
class LogEventTest {

    @Test
    void logLevelShouldBeMandatory() {
        assertThatCode(() -> new LogEvent(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, null, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new LogEvent(null, null, null)).isInstanceOf(NullPointerException.class)
                .hasMessage(null).hasNoCause();
    }

    @Test
    void gettersShouldReturnTheRightValues() {
        String message = "testMessage";
        Throwable cause = new IOException("simulated");
        LogEvent event = new LogEvent(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, message, cause);

        assertThat(event.getLevel()).isSameAs(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO);
        assertThat(event.getMessage()).isSameAs(message);
        assertThat(event.getCause()).isSameAs(cause);
    }

}
