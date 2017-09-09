/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * Uptime metrics.
 *
 * @author Michael Weirauch
 */
public class UptimeMetrics implements MeterBinder {

    private final RuntimeMXBean runtimeMXBean;
    private final Iterable<Tag> tags;

    public UptimeMetrics() {
        this(emptyList());
    }

    public UptimeMetrics(Iterable<Tag> tags) {
        this(ManagementFactory.getRuntimeMXBean(), tags);
    }

    // VisibleForTesting
    UptimeMetrics(RuntimeMXBean runtimeMXBean, Iterable<Tag> tags) {
        this.runtimeMXBean = requireNonNull(runtimeMXBean);
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.more().timeGauge(registry.createId("uptime", tags, "The uptime of the Java virtual machine"),
            runtimeMXBean, TimeUnit.MILLISECONDS, x -> Long.valueOf(x.getUptime()).doubleValue());
    }
}
