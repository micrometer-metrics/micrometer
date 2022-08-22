/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.common;

/**
 * Represents an event used for documenting instrumentation.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface Event {
    /**
     * Returns event name. Use can use {@code %s} to represent dynamic entries that
     * should be resolved at runtime via {@link String#format(String, Object...)}.
     * @return event name
     */
    String getName();

    /**
     * Returns event contextual name. Use can use {@code %s} to represent dynamic entries that
     * should be resolved at runtime via {@link String#format(String, Object...)}.
     * @return event contextual name
     */
    default String getContextualName() {
        return getName();
    }

    /**
     * Creates a {@link Event} for the given names.
     * @param name event name
     * @param contextualName event contextual name
     * @return KeyValue
     */
    static Event of(String name, String contextualName) {
        return new Event() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getContextualName() {
                return contextualName;
            }
        };
    }

    /**
     * Creates a {@link Event} for the given name.
     * @param name event name
     * @return KeyValue
     */
    static Event of(String name) {
        return of(name, name);
    }

    /**
     * Creates an event for the given key name.
     * @param dynamicEntriesForContextualName variables to be resolved in {@link Event#getContextualName()} via {@link String#format(String, Object...)}
     * @return event
     */
    default Event format(Object... dynamicEntriesForContextualName) {
        Event parent = this;
        return new Event() {

            @Override
            public String getName() {
                return parent.getName();
            }

            @Override
            public String getContextualName() {
                return String.format(parent.getContextualName(), dynamicEntriesForContextualName);
            }
        };
    }
}
