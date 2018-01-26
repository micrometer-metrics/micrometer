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
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Record metrics related to CPU utilization
 */
@NonNullApi
@NonNullFields
public class ProcessorMetrics implements MeterBinder {
    private final Iterable<Tag> tags;

    @Nullable
    private OperatingSystemMXBean operatingSystemBean;

    @Nullable
    private Method systemCpuUsage;

    @Nullable
    private Method processCpuUsage;

    public ProcessorMetrics() {
        this(emptyList());
    }

    public ProcessorMetrics(Iterable<Tag> tags) {
        this.tags = tags;
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.systemCpuUsage = detectMethod(operatingSystemBean, "getSystemCpuLoad");
        this.processCpuUsage = detectMethod(operatingSystemBean, "getProcessCpuLoad");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();
        Gauge.builder("system.cpu.count", runtime, Runtime::availableProcessors)
            .tags(tags)
            .description("The number of processors available to the Java virtual machine")
            .register(registry);

        if (operatingSystemBean != null && operatingSystemBean.getSystemLoadAverage() >= 0) {
            Gauge.builder("system.load.average.1m", operatingSystemBean, OperatingSystemMXBean::getSystemLoadAverage)
                .tags(tags)
                .description("The sum of the number of runnable entities queued to available processors and the number " +
                    "of runnable entities running on the available processors averaged over a period of time")
                .register(registry);
        }

        if (systemCpuUsage != null) {
            Gauge.builder("system.cpu.usage", operatingSystemBean, x -> invoke(x, systemCpuUsage))
                .tags(tags)
                .description("The \"recent cpu usage\" for the whole system")
                .register(registry);
        }

        if (processCpuUsage != null) {
            Gauge.builder("process.cpu.usage", operatingSystemBean, x -> invoke(x, processCpuUsage))
                .tags(tags)
                .description("The \"recent cpu usage\" for the Java Virtual Machine process")
                .register(registry);
        }
    }

    private double invoke(OperatingSystemMXBean osBean, @Nullable Method method) {
        try {
            return method != null ? (double) method.invoke(osBean) : Double.NaN;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    @Nullable
    private Method detectMethod(@Nullable OperatingSystemMXBean osBean, String name) {
        requireNonNull(name);
        if (osBean == null)
            return null;
        try {
            final Method method = osBean.getClass().getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            // Can't get more specific about type of exception here, since JDK9 will throw a
            // InaccessibleObjectException on OperatingSystemMXBean, and this exception type
            // is not available in Java 8 and prior.
            return null;
        }
    }
}
