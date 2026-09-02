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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Record metrics that report memory usage.
 *
 * @author Fábio C. Martins
 */
@NonNullApi
@NonNullFields
public class MemoryMetrics implements MeterBinder {

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
            "com.ibm.lang.management.OperatingSystemMXBean", // J9
            "com.sun.management.OperatingSystemMXBean" // HotSpot
    );

    private final OperatingSystemMXBean operatingSystemBean;

    @Nullable
    private final Class<?> operatingSystemBeanClass;

    private final Iterable<Tag> tags;

    @Nullable
    private final Method committedVirtualMemorySize;

    @Nullable
    private final Method totalSwapSpaceSize;

    @Nullable
    private final Method freeSwapSpaceSize;

    @Nullable
    private final Method freePhysicalMemorySize;

    @Nullable
    private final Method totalPhysicalMemorySize;

    public MemoryMetrics() {
        this(emptyList());
    }

    public MemoryMetrics(Iterable<Tag> tags) {
        this.tags = tags;
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        this.committedVirtualMemorySize = detectMethod("getCommittedVirtualMemorySize");
        this.totalSwapSpaceSize = detectMethod("getTotalSwapSpaceSize");
        this.freeSwapSpaceSize = detectMethod("getFreeSwapSpaceSize");
        this.freePhysicalMemorySize = detectMethod("getFreePhysicalMemorySize");
        this.totalPhysicalMemorySize = detectMethod("getTotalPhysicalMemorySize");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();
        if (committedVirtualMemorySize != null) {
            Gauge
                .builder("process.virtual_memory.commited", operatingSystemBean,
                        x -> invoke(committedVirtualMemorySize))
                .tags(tags)
                .baseUnit(BaseUnits.BYTES)
                .description(
                        "The amount of virtual memory in bytes that is commited for the Java virtual machine to use")
                .register(registry);
        }

        if (totalSwapSpaceSize != null) {
            Gauge.builder("system.swap.total", operatingSystemBean, x -> invoke(totalSwapSpaceSize))
                .tags(tags)
                .baseUnit(BaseUnits.BYTES)
                .description("The total amount of swap space in bytes")
                .register(registry);
        }

        if (freeSwapSpaceSize != null) {
            Gauge.builder("system.swap.free", operatingSystemBean, x -> invoke(freeSwapSpaceSize))
                .tags(tags)
                .baseUnit(BaseUnits.BYTES)
                .description("The amount of free swap space in bytes")
                .register(registry);
        }

        if (freePhysicalMemorySize != null) {
            Gauge.builder("system.memory.free", operatingSystemBean, x -> invoke(freePhysicalMemorySize))
                .tags(tags)
                .baseUnit(BaseUnits.BYTES)
                .description("The amount of free physical memory in bytes")
                .register(registry);
        }

        if (totalPhysicalMemorySize != null) {
            Gauge.builder("system.memory.total", operatingSystemBean, x -> invoke(totalPhysicalMemorySize))
                .tags(tags)
                .baseUnit(BaseUnits.BYTES)
                .description("The total amount of physical memory in  bytes")
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
