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
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Record metrics related to the CPU, gathered by the JVM.
 * <p>
 * Supported JVM implementations:
 * <ul>
 * <li>HotSpot</li>
 * <li>J9</li>
 * </ul>
 *
 * @author Jon Schneider
 * @author Michael Weirauch
 * @author Clint Checketts
 * @author Tommy Ludwig
 */
@NonNullApi
@NonNullFields
public class ProcessorMetrics implements MeterBinder {

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
            "com.ibm.lang.management.OperatingSystemMXBean", // J9
            "com.sun.management.OperatingSystemMXBean" // HotSpot
    );

    private final Iterable<Tag> tags;

    private final OperatingSystemMXBean operatingSystemBean;

    @Nullable
    private final Class<?> operatingSystemBeanClass;

    @Nullable
    private final Method systemCpuUsage;

    @Nullable
    private final Method processCpuUsage;

    @Nullable
    private final Method processCpuTime;

    public ProcessorMetrics() {
        this(emptyList());
    }

    public ProcessorMetrics(Iterable<Tag> tags) {
        this.tags = tags;
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        Method getCpuLoad = detectMethod("getCpuLoad");
        this.systemCpuUsage = getCpuLoad != null ? getCpuLoad : detectMethod("getSystemCpuLoad");
        this.processCpuUsage = detectMethod("getProcessCpuLoad");
        this.processCpuTime = detectMethod("getProcessCpuTime");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();
        Gauge.builder("system.cpu.count", runtime, Runtime::availableProcessors)
            .tags(tags)
            .description("The number of processors available to the Java virtual machine")
            .register(registry);

        if (operatingSystemBean.getSystemLoadAverage() >= 0) {
            Gauge.builder("system.load.average.1m", operatingSystemBean, OperatingSystemMXBean::getSystemLoadAverage)
                .tags(tags)
                .description("The sum of the number of runnable entities queued to available processors and the number "
                        + "of runnable entities running on the available processors averaged over a period of time")
                .register(registry);
        }

        if (systemCpuUsage != null) {
            Gauge.builder("system.cpu.usage", operatingSystemBean, x -> invoke(systemCpuUsage))
                .tags(tags)
                .description("The \"recent cpu usage\" of the system the application is running in")
                .register(registry);
        }

        if (processCpuUsage != null) {
            Gauge.builder("process.cpu.usage", operatingSystemBean, x -> invoke(processCpuUsage))
                .tags(tags)
                .description("The \"recent cpu usage\" for the Java Virtual Machine process")
                .register(registry);
        }

        if (processCpuTime != null) {
            FunctionCounter.builder("process.cpu.time", operatingSystemBean, x -> invoke(processCpuTime))
                .tags(tags)
                .description("The \"cpu time\" used by the Java Virtual Machine process")
                .baseUnit("ns")
                .register(registry);
        }
    }

    private double invoke(@Nullable Method method) {
        try {
            return method != null ? toDouble((Number) method.invoke(operatingSystemBean)) : Double.NaN;
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    private double toDouble(@Nullable Number number) {
        return number != null ? number.doubleValue() : Double.NaN;
    }

    @Nullable
    private Method detectMethod(String name) {
        requireNonNull(name);
        if (operatingSystemBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            operatingSystemBeanClass.cast(operatingSystemBean);
            return operatingSystemBeanClass.getMethod(name);
        }
        catch (ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    @Nullable
    private Class<?> getFirstClassFound(List<String> classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            }
            catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

}
