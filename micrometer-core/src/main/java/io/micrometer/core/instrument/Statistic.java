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

/**
 * A description of the value contained in a measurement.
 *
 * @author Jon Schneider
 */
public enum Statistic {

    /**
     * The sum of the amounts recorded.
     */
    TOTAL("total"),

    /**
     * The sum of the times recorded. Reported in the monitoring system's base unit of
     * time
     */
    TOTAL_TIME("total"),

    /**
     * Rate per second for calls.
     */
    COUNT("count"),

    /**
     * The maximum amount recorded. When this represents a time, it is reported in the
     * monitoring system's base unit of time.
     */
    MAX("max"),

    /**
     * Instantaneous value, such as those reported by gauges.
     */
    VALUE("value"),

    /**
     * Undetermined.
     */
    UNKNOWN("unknown"),

    /**
     * Number of currently active tasks for a long task timer.
     */
    ACTIVE_TASKS("active"),

    /**
     * Duration of a running task in a long task timer. Always reported in the monitoring
     * system's base unit of time.
     */
    DURATION("duration");

    private final String tagValueRepresentation;

    Statistic(String tagValueRepresentation) {
        this.tagValueRepresentation = tagValueRepresentation;
    }

    public String getTagValueRepresentation() {
        return tagValueRepresentation;
    }

}
