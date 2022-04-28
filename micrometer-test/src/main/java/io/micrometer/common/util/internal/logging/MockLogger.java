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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.micrometer.common.util.internal.logging.InternalLogLevel.DEBUG;
import static io.micrometer.common.util.internal.logging.InternalLogLevel.ERROR;
import static io.micrometer.common.util.internal.logging.InternalLogLevel.INFO;
import static io.micrometer.common.util.internal.logging.InternalLogLevel.TRACE;
import static io.micrometer.common.util.internal.logging.InternalLogLevel.WARN;

/**
 * Simple implementation of {@link InternalLogger} that does not produce any output or delegate the work to another logger,
 * instead it stores every log event in memory so that the recorded log events can be fetched and verified by tests.
 *
 * You should not create instances of this class directly, instead you can use the {@link InternalLoggerFactory} to get one.
 *
 * @author Jonatan Ivanov
 */
public class MockLogger implements InternalLogger {
    private final String name;
    private final Queue<LogEvent> logEvents = new ConcurrentLinkedQueue<>();

    MockLogger(String name) {
        this.name = name;
    }

    /**
     * @return The recorded {@link LogEvent} instances, in descending order by age (the oldest is the first one).
     */
    public List<LogEvent> getLogEvents() {
        return new ArrayList<>(logEvents);
    }

    /**
     * Removes all the events that were recorded so far.
     */
    public void clear() {
        logEvents.clear();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void trace(String msg) {
        log(TRACE, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        log(TRACE, format, arg);
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        log(TRACE, format, argA, argB);
    }

    @Override
    public void trace(String format, Object... arguments) {
        log(TRACE, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(TRACE, msg, t);
    }

    @Override
    public void trace(Throwable t) {
        log(TRACE, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        log(DEBUG, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        log(DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        log(DEBUG, format, argA, argB);
    }

    @Override
    public void debug(String format, Object... arguments) {
        log(DEBUG, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(DEBUG, msg, t);
    }

    @Override
    public void debug(Throwable t) {
        log(DEBUG, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        log(INFO, msg);
    }

    @Override
    public void info(String format, Object arg) {
        log(INFO, format, arg);
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        log(INFO, format, argA, argB);
    }

    @Override
    public void info(String format, Object... arguments) {
        log(INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(INFO, msg, t);
    }

    @Override
    public void info(Throwable t) {
        log(INFO, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        log(WARN, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        log(WARN, format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(WARN, format, arguments);
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        log(WARN, format, argA, argB);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(WARN, msg, t);
    }

    @Override
    public void warn(Throwable t) {
        log(WARN, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        log(ERROR, msg);
    }

    @Override
    public void error(String format, Object arg) {
        log(ERROR, format, arg);
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        log(ERROR, format, argA, argB);
    }

    @Override
    public void error(String format, Object... arguments) {
        log(ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(ERROR, msg, t);
    }

    @Override
    public void error(Throwable t) {
        log(ERROR, t);
    }

    @Override
    public boolean isEnabled(InternalLogLevel level) {
        return true;
    }

    @Override
    public void log(InternalLogLevel level, String msg) {
        log(level, msg, (Throwable) null);
    }

    @Override
    public void log(InternalLogLevel level, String format, Object arg) {
        log(level, MessageFormatter.format(format, arg));
    }

    @Override
    public void log(InternalLogLevel level, String format, Object argA, Object argB) {
        log(level, MessageFormatter.format(format, argA, argB));
    }

    @Override
    public void log(InternalLogLevel level, String format, Object... arguments) {
        log(level, MessageFormatter.arrayFormat(format, arguments));
    }

    private void log(InternalLogLevel level, FormattingTuple formattingTuple) {
        log(level, formattingTuple.getMessage(), formattingTuple.getThrowable());
    }

    @Override
    public void log(InternalLogLevel level, String msg, Throwable t) {
        logEvents.add(new LogEvent(level, msg, t));
    }

    @Override
    public void log(InternalLogLevel level, Throwable t) {
        log(level, null, t);
    }
}
