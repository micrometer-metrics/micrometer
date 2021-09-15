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

import org.junit.jupiter.api.Test;

import static io.micrometer.core.util.internal.logging.InternalLogLevel.INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Jonatan Ivanov
 */
class LogEventTest {

    @Test
    void logLevelShouldBeMandatory() {
        assertThatCode(() -> new LogEvent(INFO, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new LogEvent(null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(null)
                .hasNoCause();
    }

    @Test
    void gettersShouldReturnTheRightValues() {
        String message = "testMessage";
        Throwable cause = new IOException("simulated");
        LogEvent event = new LogEvent(INFO, message, cause);

        assertThat(event.getLevel()).isSameAs(INFO);
        assertThat(event.getMessage()).isSameAs(message);
        assertThat(event.getCause()).isSameAs(cause);
    }
}
