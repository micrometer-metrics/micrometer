/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.config;

import io.micrometer.common.lang.Nullable;

/**
 * Signals that a piece of provided configuration is not acceptable for some reason. For
 * example negative SLO boundaries.
 */
public class InvalidConfigurationException extends IllegalStateException {

    /**
     * Construct an exception indication invalid configuration with the specified detail
     * message and cause.
     * @param message the detail message (which is saved for later retrieval by the
     * {@link Throwable#getMessage()} method).
     * @param cause the cause (which is saved for later retrieval by the
     * {@link Throwable#getCause()} method). (A {@code null} value is permitted, and
     * indicates that the cause is nonexistent or unknown.)
     * @since 1.11.9
     */
    public InvalidConfigurationException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public InvalidConfigurationException(String message) {
        super(message);
    }

}
