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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple implementation of {@link InternalLogger} that does not produce any output or
 * delegate the work to another logger, instead it stores every log event in memory so
 * that the recorded log events can be fetched and verified by tests.
 *
 * You should not create instances of this class directly, instead you can use the
 * {@link InternalLoggerFactory} to get one.
 *
 * @author Jonatan Ivanov
 * @deprecated Please use {@link io.micrometer.common.util.internal.logging.MockLogger}
 * instead.
 */
@Deprecated
public class MockLogger implements InternalLogger {

    private final String name;

    private final Queue<LogEvent> logEvents = new ConcurrentLinkedQueue<>();

    MockLogger(String name) {
        this.name = name;
    }

    /**
     * @return The recorded {@link LogEvent} instances, in descending order by age (the
     * oldest is the first one).
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
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, format, arg);
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, format, argA, argB);
    }

    @Override
    public void trace(String format, Object... arguments) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, msg, t);
    }

    @Override
    public void trace(Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.TRACE, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, format, argA, argB);
    }

    @Override
    public void debug(String format, Object... arguments) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, msg, t);
    }

    @Override
    public void debug(Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.DEBUG, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, msg);
    }

    @Override
    public void info(String format, Object arg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, format, arg);
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, format, argA, argB);
    }

    @Override
    public void info(String format, Object... arguments) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, msg, t);
    }

    @Override
    public void info(Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.INFO, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, format, arguments);
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, format, argA, argB);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, msg, t);
    }

    @Override
    public void warn(Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.WARN, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, msg);
    }

    @Override
    public void error(String format, Object arg) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, format, arg);
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, format, argA, argB);
    }

    @Override
    public void error(String format, Object... arguments) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, msg, t);
    }

    @Override
    public void error(Throwable t) {
        log(io.micrometer.core.util.internal.logging.InternalLogLevel.ERROR, t);
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
