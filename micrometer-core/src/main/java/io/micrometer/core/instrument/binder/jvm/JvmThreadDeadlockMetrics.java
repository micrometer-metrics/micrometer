/*
 * Copyright 2024 VMware, Inc.
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
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for JVM deadlocked threads. These metrics may be expensive to
 * collect. Consider that when deciding whether to enable these metrics. To enable these
 * metrics, bind an instance of this to a {@link MeterRegistry}.
 *
 * @author Ruth Kurniawati
 * @since 1.14.0
 */
@NonNullApi
@NonNullFields
public class JvmThreadDeadlockMetrics implements MeterBinder {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(JvmThreadDeadlockMetrics.class);

    private final Iterable<Tag> tags;

    public JvmThreadDeadlockMetrics() {
        this(emptyList());
    }

    public JvmThreadDeadlockMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        if (threadBean.isSynchronizerUsageSupported()) {
            Gauge.builder("jvm.threads.deadlocked", threadBean, JvmThreadDeadlockMetrics::getDeadlockedThreadCount)
                .tags(tags)
                .description("The current number of threads that are deadlocked")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);
        }
        else {
            log.warn("jvm.threads.deadlocked is not available on this JVM");
        }

        Gauge
            .builder("jvm.threads.deadlocked.monitor", threadBean,
                    JvmThreadDeadlockMetrics::getDeadlockedMonitorThreadCount)
            .tags(tags)
            .description("The current number of threads that are deadlocked on object monitors")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);
    }

    // VisibleForTesting
    static long getDeadlockedThreadCount(ThreadMXBean threadBean) {
        final long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        return deadlockedThreads == null ? 0 : deadlockedThreads.length;
    }

    static long getDeadlockedMonitorThreadCount(ThreadMXBean threadBean) {
        final long[] monitorDeadlockedThreads = threadBean.findMonitorDeadlockedThreads();
        return monitorDeadlockedThreads == null ? 0 : monitorDeadlockedThreads.length;
    }

}
