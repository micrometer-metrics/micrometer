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

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for JVM compilation metrics.
 *
 * @deprecated Scheduled for removal in 2.0.0, please use {@code io.micrometer.binder.jvm.JvmCompilationMetrics}
 * @since 1.4.0
 */
@NonNullApi
@NonNullFields
@Deprecated
public class JvmCompilationMetrics implements MeterBinder {
    private final Iterable<? extends io.micrometer.common.Tag> tags;

    public JvmCompilationMetrics() {
        this(emptyList());
    }

    public JvmCompilationMetrics(Iterable<? extends io.micrometer.common.Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        CompilationMXBean compilationBean = ManagementFactory.getCompilationMXBean();
        if (compilationBean != null && compilationBean.isCompilationTimeMonitoringSupported()) {
            FunctionCounter.builder("jvm.compilation.time", compilationBean, CompilationMXBean::getTotalCompilationTime)
                    .tags(io.micrometer.common.Tags.concat(tags, "compiler", compilationBean.getName()))
                    .description("The approximate accumulated elapsed time spent in compilation")
                    .baseUnit(BaseUnits.MILLISECONDS)
                    .register(registry);
        }
    }
}
