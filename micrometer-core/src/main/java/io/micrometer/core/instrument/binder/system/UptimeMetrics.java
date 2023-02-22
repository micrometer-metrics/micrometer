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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * Uptime metrics.
 *
 * @author Michael Weirauch
 */
@NonNullApi
@NonNullFields
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
        this.runtimeMXBean = runtimeMXBean;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        TimeGauge.builder("process.uptime", runtimeMXBean, TimeUnit.MILLISECONDS, RuntimeMXBean::getUptime)
            .tags(tags)
            .description("The uptime of the Java virtual machine")
            .register(registry);

        TimeGauge.builder("process.start.time", runtimeMXBean, TimeUnit.MILLISECONDS, RuntimeMXBean::getStartTime)
            .tags(tags)
            .description("Start time of the process since unix epoch.")
            .register(registry);
    }

}
