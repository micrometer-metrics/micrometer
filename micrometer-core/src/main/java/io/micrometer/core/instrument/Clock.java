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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.step.StepLong;

/**
 * Used to measure absolute and relative time.
 *
 * @see MockClock for a clock that can be manually advanced for use in tests.
 * @author Jon Schneider
 */
public interface Clock {

    Clock SYSTEM = new Clock() {
        @Override
        public long wallTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long monotonicTime() {
            return System.nanoTime();
        }
    };

    /**
     * Current wall time in milliseconds since the epoch. Typically equivalent to
     * System.currentTimeMillis. Should not be used to determine durations. Used for
     * timestamping metrics being pushed to a monitoring system or for determination of
     * step boundaries (e.g. {@link StepLong}.
     * @return Wall time in milliseconds
     */
    long wallTime();

    /**
     * Current time from a monotonic clock source. The value is only meaningful when
     * compared with another snapshot to determine the elapsed time for an operation. The
     * difference between two samples will have a unit of nanoseconds. The returned value
     * is typically equivalent to System.nanoTime.
     * @return Monotonic time in nanoseconds
     */
    long monotonicTime();

}
