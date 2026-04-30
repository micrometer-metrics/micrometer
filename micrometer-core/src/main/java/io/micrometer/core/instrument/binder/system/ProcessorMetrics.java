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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.convention.JvmCpuCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmCpuLoadMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmCpuMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.JvmCpuTimeMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmCpuCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmCpuLoadMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmCpuTimeMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuLoadMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuTimeMeterConvention;
import org.jspecify.annotations.Nullable;

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
public class ProcessorMetrics implements MeterBinder {

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
            "com.ibm.lang.management.OperatingSystemMXBean", // J9
            "com.sun.management.OperatingSystemMXBean" // HotSpot
    );

    private final Tags extraTags;

    private final JvmCpuTimeMeterConvention cpuTimeConvention;

    private final JvmCpuCountMeterConvention cpuCountConvention;

    private final JvmCpuLoadMeterConvention cpuLoadConvention;

    private final OperatingSystemMXBean operatingSystemBean;

    private final @Nullable Class<?> operatingSystemBeanClass;

    private final @Nullable Method systemCpuUsage;

    private final @Nullable Method processCpuUsage;

    private final @Nullable Method processCpuTime;

    public ProcessorMetrics() {
        this(emptyList());
    }

    /**
     * Uses the default convention with the provided extra tags.
     * @param extraTags tags to add to each meter's tags produced by this binder
     */
    public ProcessorMetrics(Iterable<Tag> extraTags) {
        this(Tags.of(extraTags), new MicrometerJvmCpuTimeMeterConvention(), new MicrometerJvmCpuCountMeterConvention(),
                new MicrometerJvmCpuLoadMeterConvention());
    }

    /**
     * The supplied extra tags are not combined with the convention. Provide a convention
     * that applies the extra tags if that is the desired outcome. The convention only
     * applies to some meters.
     * @param extraTags extra tags to add to meters not covered by the conventions
     * @param conventions custom conventions for applicable meters
     * @since 1.16.0
     * @deprecated use {@link #builder()} to provide individual conventions
     */
    @Deprecated
    public ProcessorMetrics(Iterable<? extends Tag> extraTags, JvmCpuMeterConventions conventions) {
        this(Tags.of(extraTags),
                JvmCpuTimeMeterConvention.of(conventions.cpuTimeConvention().getName(),
                        conventions.cpuTimeConvention().getTags(null)),
                JvmCpuCountMeterConvention.of(conventions.cpuCountConvention().getName(),
                        conventions.cpuCountConvention().getTags(null)),
                JvmCpuLoadMeterConvention.of(conventions.processCpuLoadConvention().getName(),
                        conventions.processCpuLoadConvention().getTags(null)));
    }

    private ProcessorMetrics(Tags extraTags, JvmCpuTimeMeterConvention cpuTimeConvention,
            JvmCpuCountMeterConvention cpuCountConvention, JvmCpuLoadMeterConvention cpuLoadConvention) {
        this.extraTags = extraTags;
        this.cpuTimeConvention = cpuTimeConvention;
        this.cpuCountConvention = cpuCountConvention;
        this.cpuLoadConvention = cpuLoadConvention;
        this.operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystemBeanClass = getFirstClassFound(OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        Method getCpuLoad = detectMethod("getCpuLoad");
        this.systemCpuUsage = getCpuLoad != null ? getCpuLoad : detectMethod("getSystemCpuLoad");
        this.processCpuUsage = detectMethod("getProcessCpuLoad");
        this.processCpuTime = detectMethod("getProcessCpuTime");
    }

    /**
     * Create a new builder for {@link ProcessorMetrics}.
     * @return a new builder
     * @since 1.16.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Runtime runtime = Runtime.getRuntime();
        Gauge.builder(cpuCountConvention.getName(), runtime, Runtime::availableProcessors)
            .tags(cpuCountConvention.getTags(null))
            .tags(extraTags)
            .description("The number of processors available to the Java virtual machine")
            .register(registry);

        if (operatingSystemBean.getSystemLoadAverage() >= 0) {
            Gauge.builder("system.load.average.1m", operatingSystemBean, OperatingSystemMXBean::getSystemLoadAverage)
                .tags(extraTags)
                .description("The sum of the number of runnable entities queued to available processors and the number "
                        + "of runnable entities running on the available processors averaged over a period of time")
                .register(registry);
        }

        if (systemCpuUsage != null) {
            Gauge.builder("system.cpu.usage", operatingSystemBean, x -> invoke(systemCpuUsage))
                .tags(extraTags)
                .description("The \"recent cpu usage\" of the system the application is running in")
                .register(registry);
        }

        if (processCpuUsage != null) {
            Gauge.builder(cpuLoadConvention.getName(), operatingSystemBean, x -> invoke(processCpuUsage))
                .tags(cpuLoadConvention.getTags(null))
                .tags(extraTags)
                .description("The \"recent cpu usage\" for the Java Virtual Machine process")
                .register(registry);
        }

        if (processCpuTime != null) {
            FunctionCounter.builder(cpuTimeConvention.getName(), operatingSystemBean, x -> invoke(processCpuTime))
                .tags(cpuTimeConvention.getTags(null))
                .tags(extraTags)
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

    private @Nullable Method detectMethod(String name) {
        requireNonNull(name);
        if (operatingSystemBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            Object ignored = operatingSystemBeanClass.cast(operatingSystemBean);
            return operatingSystemBeanClass.getMethod(name);
        }
        catch (ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    private @Nullable Class<?> getFirstClassFound(List<String> classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            }
            catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

    /**
     * Builder for {@link ProcessorMetrics}.
     *
     * @since 1.16.0
     */
    public static class Builder {

        private Tags extraTags = Tags.empty();

        private @Nullable JvmCpuTimeMeterConvention cpuTimeConvention;

        private @Nullable JvmCpuCountMeterConvention cpuCountConvention;

        private @Nullable JvmCpuLoadMeterConvention cpuLoadConvention;

        Builder() {
        }

        /**
         * Extra tags to add to meters registered by this binder.
         * @param extraTags tags to add
         * @return this builder
         */
        public Builder extraTags(Iterable<? extends Tag> extraTags) {
            this.extraTags = Tags.of(extraTags);
            return this;
        }

        /**
         * Custom convention for the CPU time meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder cpuTimeConvention(JvmCpuTimeMeterConvention convention) {
            this.cpuTimeConvention = convention;
            return this;
        }

        /**
         * Custom convention for the CPU count meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder cpuCountConvention(JvmCpuCountMeterConvention convention) {
            this.cpuCountConvention = convention;
            return this;
        }

        /**
         * Custom convention for the process CPU load meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder cpuLoadConvention(JvmCpuLoadMeterConvention convention) {
            this.cpuLoadConvention = convention;
            return this;
        }

        /**
         * Use OpenTelemetry semantic conventions for all meters. Individual conventions
         * can still be overridden by calling the specific convention methods after this
         * one.
         * @return this builder
         */
        public Builder openTelemetryConventions() {
            this.cpuTimeConvention = new OpenTelemetryJvmCpuTimeMeterConvention();
            this.cpuCountConvention = new OpenTelemetryJvmCpuCountMeterConvention();
            this.cpuLoadConvention = new OpenTelemetryJvmCpuLoadMeterConvention();
            return this;
        }

        /**
         * Build a new {@link ProcessorMetrics} instance.
         * @return a new {@link ProcessorMetrics}
         */
        public ProcessorMetrics build() {
            return new ProcessorMetrics(extraTags,
                    cpuTimeConvention != null ? cpuTimeConvention : new MicrometerJvmCpuTimeMeterConvention(),
                    cpuCountConvention != null ? cpuCountConvention : new MicrometerJvmCpuCountMeterConvention(),
                    cpuLoadConvention != null ? cpuLoadConvention : new MicrometerJvmCpuLoadMeterConvention());
        }

    }

}
