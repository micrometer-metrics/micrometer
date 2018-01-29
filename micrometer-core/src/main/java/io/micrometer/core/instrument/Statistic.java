/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

/**
 * A description of the value contained in a measurement.
 *
 * @author Jon Schneider
 */
public enum Statistic {
    /**
     * The sum of the amounts recorded.
     */
    Total,

    /**
     * The sum of the times recorded. Always reported in nanoseconds.
     */
    TotalTime,

    /**
     * Rate per second for calls.
     */
    Count,

    /**
     * The maximum amount recorded. When this represents a time, it is always reported in nanoseconds.
     */
    Max,

    /**
     * Instantaneous value, such as those reported by gauges.
     **/
    Value,

    /**
     * Undetermined.
     */
    Unknown,

    /**
     * Number of currently active tasks for a long task timer.
     */
    ActiveTasks,

    /**
     * Duration of a running task in a long task timer. Always reported in nanoseconds.
     */
    Duration,
}
