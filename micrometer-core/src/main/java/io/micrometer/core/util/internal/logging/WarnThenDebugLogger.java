/*
 * Copyright 2019 VMware, Inc.
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * {@link InternalLogger} which logs at warn level at first and then logs at debug level
 * for the rest.
 *
 * @author Johnny Lim
 * @since 1.1.8
 * @deprecated Please use
 * {@link io.micrometer.common.util.internal.logging.WarnThenDebugLogger} instead.
 */
@Deprecated
public class WarnThenDebugLogger {

    private final InternalLogger logger;

    private final AtomicBoolean warnLogged = new AtomicBoolean();

    public WarnThenDebugLogger(Class<?> clazz) {
        this.logger = InternalLoggerFactory.getInstance(clazz);
    }

    public void log(String message, Throwable ex) {
        if (this.warnLogged.compareAndSet(false, true)) {
            log(InternalLogLevel.WARN, getWarnMessage(message), ex);
        }
        else {
            log(InternalLogLevel.DEBUG, message, ex);
        }
    }

    private String getWarnMessage(String message) {
        return message + " Note that subsequent logs will be logged at debug level.";
    }

    private void log(InternalLogLevel level, String finalMessage, Throwable ex) {
        if (ex != null) {
            this.logger.log(level, finalMessage, ex);
        }
        else {
            this.logger.log(level, finalMessage);
        }
    }

    public void log(String message) {
        log(message, null);
    }

    public void log(Supplier<String> messageSupplier, Throwable ex) {
        if (this.warnLogged.compareAndSet(false, true)) {
            log(InternalLogLevel.WARN, getWarnMessage(messageSupplier.get()), ex);
        }
        else {
            if (this.logger.isDebugEnabled()) {
                log(InternalLogLevel.DEBUG, messageSupplier.get(), ex);
            }
        }
    }

    public void log(Supplier<String> messageSupplier) {
        log(messageSupplier, null);
    }

}
