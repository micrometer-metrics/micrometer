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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Locale;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for JVM threads.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@NonNullApi
@NonNullFields
public class JvmThreadMetrics implements MeterBinder {

    private final Iterable<Tag> tags;

    public JvmThreadMetrics() {
        this(emptyList());
    }

    public JvmThreadMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Gauge.builder("jvm.threads.peak", threadBean, ThreadMXBean::getPeakThreadCount)
            .tags(tags)
            .description("The peak live thread count since the Java virtual machine started or peak was reset")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        Gauge.builder("jvm.threads.daemon", threadBean, ThreadMXBean::getDaemonThreadCount)
            .tags(tags)
            .description("The current number of live daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        Gauge.builder("jvm.threads.live", threadBean, ThreadMXBean::getThreadCount)
            .tags(tags)
            .description("The current number of live threads including both daemon and non-daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        FunctionCounter.builder("jvm.threads.started", threadBean, ThreadMXBean::getTotalStartedThreadCount)
            .tags(tags)
            .description("The total number of application threads started in the JVM")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        try {
            threadBean.getAllThreadIds();
            for (Thread.State state : Thread.State.values()) {
                Gauge.builder("jvm.threads.states", threadBean, (bean) -> getThreadStateCount(bean, state))
                    .tags(Tags.concat(tags, "state", getStateTagValue(state)))
                    .description("The current number of threads")
                    .baseUnit(BaseUnits.THREADS)
                    .register(registry);
            }
        }
        catch (Error error) {
            // An error will be thrown for unsupported operations
            // e.g. SubstrateVM does not support getAllThreadIds
        }
    }

    // VisibleForTesting
    static long getThreadStateCount(ThreadMXBean threadBean, Thread.State state) {
        return Arrays.stream(threadBean.getThreadInfo(threadBean.getAllThreadIds()))
            .filter(threadInfo -> threadInfo != null && threadInfo.getThreadState() == state)
            .count();
    }

    private static String getStateTagValue(Thread.State state) {
        return state.name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

}
