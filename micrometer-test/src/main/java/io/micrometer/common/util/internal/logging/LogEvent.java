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
package io.micrometer.common.util.internal.logging;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Simple POJO that represents a log event for test verification purposes.
 *
 * @author Jonatan Ivanov
 */
public class LogEvent {

    private final InternalLogLevel level;

    private final @Nullable String message;

    private final @Nullable Throwable cause;

    /**
     * @param level The level of the log event (INFO, WARN, ERROR, etc.) (mandatory).
     * @param message The message to be logged (textual description about what happened)
     * (optional).
     * @param cause The {@link Throwable} that triggered the log event (optional).
     */
    public LogEvent(InternalLogLevel level, @Nullable String message, @Nullable Throwable cause) {
        this.level = Objects.requireNonNull(level);
        this.message = message;
        this.cause = cause;
    }

    public InternalLogLevel getLevel() {
        return level;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public @Nullable Throwable getCause() {
        return cause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LogEvent))
            return false;
        LogEvent logEvent = (LogEvent) o;
        return level == logEvent.level && Objects.equals(message, logEvent.message)
                && Objects.equals(cause, logEvent.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, message, cause);
    }

    @Override
    public String toString() {
        return "LogEvent{" + "level=" + level + ", message='" + message + '\'' + ", cause=" + cause + '}';
    }

}
