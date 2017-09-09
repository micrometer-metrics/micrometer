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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

import static java.util.Collections.emptyList;

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

        registry.gauge(registry.createId("jvm.classes.loaded", tags,
            "The number of classes that are currently loaded in the Java virtual machine."),
            classLoadingBean, ClassLoadingMXBean::getLoadedClassCount);

        registry.more().counter(registry.createId("jvm.classes.unloaded", tags,
            "The total number of classes unloaded since the Java virtual machine has started execution"),
            classLoadingBean, ClassLoadingMXBean::getUnloadedClassCount);
    }
}
