/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm.convention;

import io.micrometer.core.instrument.Tags;

import java.util.function.Function;

/**
 * Convention for JVM thread count metrics.
 *
 * @see io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
 * @since 1.18.0
 */
public interface JvmThreadCountMeterConvention {

    /**
     * Canonical name of the meter.
     * @return meter name
     */
    String getName();

    /**
     * Tags specific to this meter convention.
     * @param state the thread state from which to derive tags
     * @return tags to use with the meter
     */
    default Tags getTags(Thread.State state) {
        return Tags.empty();
    }

    /**
     * Create a {@link JvmThreadCountMeterConvention} with the given name.
     * @param name meter name
     * @return a new convention instance
     */
    static JvmThreadCountMeterConvention of(String name) {
        return () -> name;
    }

    /**
     * Create a {@link JvmThreadCountMeterConvention} with the given name and tags
     * function.
     * @param name meter name
     * @param tagsFunction function to derive tags from a {@link Thread.State}
     * @return a new convention instance
     */
    static JvmThreadCountMeterConvention of(String name, Function<Thread.State, Tags> tagsFunction) {
        return new JvmThreadCountMeterConvention() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Tags getTags(Thread.State state) {
                return tagsFunction.apply(state);
            }
        };
    }

}
