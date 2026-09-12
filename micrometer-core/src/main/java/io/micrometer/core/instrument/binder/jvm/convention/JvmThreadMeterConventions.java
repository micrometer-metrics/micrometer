/*
 * Copyright 2025 VMware, Inc.
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
import io.micrometer.core.instrument.binder.MeterConvention;

/**
 * Get {@link MeterConvention} for thread related metrics.
 *
 * @see io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
 * @since 1.16.0
 */
public interface JvmThreadMeterConventions {

    MeterConvention<Thread.State> threadCountConvention();

    /**
     * Whether thread count gauges should be further broken down by daemon status. When
     * {@code true}, {@link io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics}
     * registers a gauge for each combination of {@link Thread.State} and daemon flag, and
     * combines {@link #threadCountConvention()} tags with {@link #daemonTags(boolean)}.
     * Defaults to {@code false} to preserve historical cardinality of
     * {@code jvm.threads.states}.
     * @return {@code true} to include the daemon dimension
     * @since 1.17.0
     */
    default boolean includeDaemonTag() {
        return false;
    }

    /**
     * Tags describing whether a thread is a daemon thread. Only used when
     * {@link #includeDaemonTag()} is {@code true}.
     * @param daemon whether the thread is a daemon thread
     * @return tags for the daemon dimension
     * @since 1.17.0
     */
    default Tags daemonTags(boolean daemon) {
        return Tags.of("daemon", Boolean.toString(daemon));
    }

}
