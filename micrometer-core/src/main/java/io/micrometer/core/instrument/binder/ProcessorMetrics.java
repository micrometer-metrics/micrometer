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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import javax.annotation.processing.Processor;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

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
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();
        registry.gauge(registry.createId("cpu", tags,
            "The number of processors available to the Java virtual machine"),
            runtime, Runtime::availableProcessors);

        OperatingSystemMXBean operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        if(operatingSystemBean != null && operatingSystemBean.getSystemLoadAverage() >= 0) {
            registry.gauge(registry.createId("cpu.load.average", tags,
                "The sum of the number of runnable entities queued to available processors and the number " +
                    "of runnable entities running on the available processors averaged over a period of time"),
                operatingSystemBean, OperatingSystemMXBean::getSystemLoadAverage);
        }
    }
}
