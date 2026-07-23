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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmClassCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmClassLoadedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.JvmClassUnloadedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmClassCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmClassLoadedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmClassUnloadedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassLoadedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassUnloadedMeterConvention;
import org.jspecify.annotations.Nullable;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

/**
 * Binder providing metrics related to JVM class loading.
 *
 * @see ClassLoadingMXBean
 */
public class ClassLoaderMetrics implements MeterBinder {

    private final Tags extraTags;

    private final JvmClassCountMeterConvention classCountConvention;

    private final JvmClassLoadedMeterConvention classLoadedConvention;

    private final JvmClassUnloadedMeterConvention classUnloadedConvention;

    /**
     * Class loader metrics with the default convention.
     */
    public ClassLoaderMetrics() {
        this(Tags.empty(), new MicrometerJvmClassCountMeterConvention(), new MicrometerJvmClassLoadedMeterConvention(),
                new MicrometerJvmClassUnloadedMeterConvention());
    }

    /**
     * Class loader metrics using the default convention with extra tags added.
     * @param extraTags additional tags to add to metrics registered by this binder
     */
    public ClassLoaderMetrics(Iterable<Tag> extraTags) {
        this(Tags.of(extraTags), new MicrometerJvmClassCountMeterConvention(),
                new MicrometerJvmClassLoadedMeterConvention(), new MicrometerJvmClassUnloadedMeterConvention());
    }

    /**
     * Class loader metrics registered by this binder will use the provided convention.
     * @param conventions custom convention to apply
     * @since 1.16.0
     * @deprecated use {@link #builder()} to provide individual conventions
     */
    @Deprecated
    public ClassLoaderMetrics(JvmClassLoadingMeterConventions conventions) {
        this.extraTags = Tags.empty();
        MeterConvention<Object> count = conventions.currentClassCountConvention();
        this.classCountConvention = JvmClassCountMeterConvention.of(count.getName(), count.getTags(null));
        MeterConvention<Object> loaded = conventions.loadedConvention();
        this.classLoadedConvention = JvmClassLoadedMeterConvention.of(loaded.getName(), loaded.getTags(null));
        MeterConvention<Object> unloaded = conventions.unloadedConvention();
        this.classUnloadedConvention = JvmClassUnloadedMeterConvention.of(unloaded.getName(), unloaded.getTags(null));
    }

    private ClassLoaderMetrics(Tags extraTags, JvmClassCountMeterConvention classCountConvention,
            JvmClassLoadedMeterConvention classLoadedConvention,
            JvmClassUnloadedMeterConvention classUnloadedConvention) {
        this.extraTags = extraTags;
        this.classCountConvention = classCountConvention;
        this.classLoadedConvention = classLoadedConvention;
        this.classUnloadedConvention = classUnloadedConvention;
    }

    /**
     * Create a new builder for {@link ClassLoaderMetrics}.
     * @return a new builder
     * @since 1.16.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();

        Gauge.builder(classCountConvention.getName(), classLoadingBean, ClassLoadingMXBean::getLoadedClassCount)
            .tags(classCountConvention.getTags(null))
            .tags(extraTags)
            .description("The number of classes that are currently loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

        FunctionCounter
            .builder(classUnloadedConvention.getName(), classLoadingBean, ClassLoadingMXBean::getUnloadedClassCount)
            .tags(classUnloadedConvention.getTags(null))
            .tags(extraTags)
            .description("The number of classes unloaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

        FunctionCounter
            .builder(classLoadedConvention.getName(), classLoadingBean, ClassLoadingMXBean::getTotalLoadedClassCount)
            .tags(classLoadedConvention.getTags(null))
            .tags(extraTags)
            .description("The number of classes loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);
    }

    /**
     * Builder for {@link ClassLoaderMetrics}.
     *
     * @since 1.16.0
     */
    public static class Builder {

        private Tags extraTags = Tags.empty();

        private @Nullable JvmClassCountMeterConvention classCountConvention;

        private @Nullable JvmClassLoadedMeterConvention classLoadedConvention;

        private @Nullable JvmClassUnloadedMeterConvention classUnloadedConvention;

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
         * Custom convention for the current class count meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder classCountConvention(JvmClassCountMeterConvention convention) {
            this.classCountConvention = convention;
            return this;
        }

        /**
         * Custom convention for the classes loaded (total) meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder classLoadedConvention(JvmClassLoadedMeterConvention convention) {
            this.classLoadedConvention = convention;
            return this;
        }

        /**
         * Custom convention for the classes unloaded meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder classUnloadedConvention(JvmClassUnloadedMeterConvention convention) {
            this.classUnloadedConvention = convention;
            return this;
        }

        /**
         * Use OpenTelemetry semantic conventions for all meters. Individual conventions
         * can still be overridden by calling the specific convention methods after this
         * one.
         * @return this builder
         */
        public Builder openTelemetryConventions() {
            this.classCountConvention = new OpenTelemetryJvmClassCountMeterConvention();
            this.classLoadedConvention = new OpenTelemetryJvmClassLoadedMeterConvention();
            this.classUnloadedConvention = new OpenTelemetryJvmClassUnloadedMeterConvention();
            return this;
        }

        /**
         * Build a new {@link ClassLoaderMetrics} instance.
         * @return a new {@link ClassLoaderMetrics}
         */
        public ClassLoaderMetrics build() {
            return new ClassLoaderMetrics(extraTags,
                    classCountConvention != null ? classCountConvention : new MicrometerJvmClassCountMeterConvention(),
                    classLoadedConvention != null ? classLoadedConvention
                            : new MicrometerJvmClassLoadedMeterConvention(),
                    classUnloadedConvention != null ? classUnloadedConvention
                            : new MicrometerJvmClassUnloadedMeterConvention());
        }

    }

}
