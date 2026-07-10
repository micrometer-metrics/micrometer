/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.observation;

import io.micrometer.observation.Observation.Event;

/**
 * Default implementation of {@link Event}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
class SimpleEvent implements Event {

    private final String name;

    private final String contextualName;

    private final long wallTime;

    SimpleEvent(String name, String contextualName) {
        this(name, contextualName, System.currentTimeMillis());
    }

    /**
     * @param name The name of the event (should have low cardinality).
     * @param contextualName The contextual name of the event (can have high cardinality).
     * @param wallTime Wall time in milliseconds since the epoch
     */
    SimpleEvent(String name, String contextualName, long wallTime) {
        this.name = name;
        this.contextualName = contextualName;
        this.wallTime = wallTime;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getContextualName() {
        return contextualName;
    }

    @Override
    public long getWallTime() {
        return this.wallTime;
    }

    @Override
    public String toString() {
        return "event.name='" + getName() + "', event.contextualName='" + getContextualName() + "', event.wallTime="
                + getWallTime();
    }

}
