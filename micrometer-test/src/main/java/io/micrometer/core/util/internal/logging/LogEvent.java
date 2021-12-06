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

import java.util.Objects;

/**
 * Simple POJO that represents a log event for test verification purposes.
 *
 * @author Jonatan Ivanov
 */
public class LogEvent {
    private final InternalLogLevel level;
    private final String message;
    private final Throwable cause;

    /**
     * @param level The level of the log event (INFO, WARN, ERROR, etc.) (mandatory).
     * @param message The message to be logged (textual description about what happened) (optional).
     * @param cause The {@link Throwable} that triggered the log event (optional).
     */
    public LogEvent(InternalLogLevel level, String message, Throwable cause) {
        this.level = Objects.requireNonNull(level);
        this.message = message;
        this.cause = cause;
    }

    public InternalLogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEvent logEvent = (LogEvent) o;
        return level == logEvent.level && Objects.equals(message, logEvent.message) && Objects.equals(cause, logEvent.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, message, cause);
    }

    @Override
    public String toString() {
        return "LogEvent{" +
                "level=" + level +
                ", message='" + message + '\'' +
                ", cause=" + cause +
                '}';
    }
}
