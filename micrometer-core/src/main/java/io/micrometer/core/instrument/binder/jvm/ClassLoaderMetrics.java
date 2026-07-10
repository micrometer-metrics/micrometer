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
import io.micrometer.core.instrument.binder.jvm.convention.JvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmClassLoadingMeterConventions;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

/**
 * Binder providing metrics related to JVM class loading.
 *
 * @see ClassLoadingMXBean
 */
public class ClassLoaderMetrics implements MeterBinder {

    private final JvmClassLoadingMeterConventions conventions;

    /**
     * Class loader metrics with the default convention.
     */
    public ClassLoaderMetrics() {
        this(new MicrometerJvmClassLoadingMeterConventions());
    }

    /**
     * Class loader metrics using the default convention with extra tags added.
     * @param extraTags additional tags to add to metrics registered by this binder
     */
    public ClassLoaderMetrics(Iterable<Tag> extraTags) {
        this(new MicrometerJvmClassLoadingMeterConventions(Tags.of(extraTags)));
    }

    /**
     * Class loader metrics registered by this binder will use the provided convention.
     * @param conventions custom convention to apply
     * @since 1.16.0
     */
    public ClassLoaderMetrics(JvmClassLoadingMeterConventions conventions) {
        this.conventions = conventions;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();

        MeterConvention<Object> currentClassCountConvention = conventions.currentClassCountConvention();
        Gauge.builder(currentClassCountConvention.getName(), classLoadingBean, ClassLoadingMXBean::getLoadedClassCount)
            .tags(currentClassCountConvention.getTags(null))
            .description("The number of classes that are currently loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

        MeterConvention<Object> unloadedConvention = conventions.unloadedConvention();
        FunctionCounter
            .builder(unloadedConvention.getName(), classLoadingBean, ClassLoadingMXBean::getUnloadedClassCount)
            .tags(unloadedConvention.getTags(null))
            .description("The number of classes unloaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

        MeterConvention<Object> loadedConvention = conventions.loadedConvention();
        FunctionCounter
            .builder(loadedConvention.getName(), classLoadingBean, ClassLoadingMXBean::getTotalLoadedClassCount)
            .tags(loadedConvention.getTags(null))
            .description("The number of classes loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);
    }

}
