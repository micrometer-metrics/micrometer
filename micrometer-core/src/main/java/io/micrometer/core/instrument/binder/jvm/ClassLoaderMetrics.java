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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

import static java.util.Collections.emptyList;

@NonNullApi
@NonNullFields
public class ClassLoaderMetrics implements MeterBinder {

    private final Iterable<Tag> tags;

    public ClassLoaderMetrics() {
        this(emptyList());
    }

    public ClassLoaderMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();

        Gauge.builder("jvm.classes.loaded", classLoadingBean, ClassLoadingMXBean::getLoadedClassCount)
            .tags(tags)
            .description("The number of classes that are currently loaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);

        FunctionCounter.builder("jvm.classes.unloaded", classLoadingBean, ClassLoadingMXBean::getUnloadedClassCount)
            .tags(tags)
            .description("The number of classes unloaded in the Java virtual machine")
            .baseUnit(BaseUnits.CLASSES)
            .register(registry);
    }

}
