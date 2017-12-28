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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import static io.micrometer.core.instrument.util.Assert.notNull;
import static java.util.Collections.emptyList;

/**
 * Record metrics related to CPU utilization
 */
public class ProcessorMetrics implements MeterBinder {
    private final Iterable<Tag> tags;

    public ProcessorMetrics() {
        this(emptyList());
    }

    public ProcessorMetrics(Iterable<Tag> tags) {
        this.tags = notNull(tags, "tags");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();

        Gauge.builder("system.cpu.count", runtime, Runtime::availableProcessors)
            .tags(tags)
            .description("The number of processors available to the Java virtual machine")
            .register(registry);

        OperatingSystemMXBean operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        if(operatingSystemBean != null && operatingSystemBean.getSystemLoadAverage() >= 0) {
            Gauge.builder("system.load.average.1m", operatingSystemBean, OperatingSystemMXBean::getSystemLoadAverage)
                .tags(tags)
                .description("The sum of the number of runnable entities queued to available processors and the number " +
                    "of runnable entities running on the available processors averaged over a period of time")
                .register(registry);
        }
    }
}
