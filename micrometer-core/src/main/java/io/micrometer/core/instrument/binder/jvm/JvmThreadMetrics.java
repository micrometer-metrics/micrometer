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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final ThreadMXBean threadBean;

    private final ThreadLocal<Map<Thread.State, Long>> threadCountsByStateThreadLocal;

    public JvmThreadMetrics() {
        this(emptyList());
    }

    public JvmThreadMetrics(Iterable<Tag> tags) {
        this.tags = tags;
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.threadCountsByStateThreadLocal = ThreadLocal.withInitial(this::getThreadCountsByStateSnapshot);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
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
                Gauge.builder("jvm.threads.states", () -> getThreadCountByState(state))
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

    private static String getStateTagValue(Thread.State state) {
        return state.name().toLowerCase().replace("_", "-");
    }

    private Long getThreadCountByState(Thread.State state) {
        // If the thread-local is empty, this will also trigger populating it
        // i.e.: getting a new snapshots for thread counts by thread state.
        Map<Thread.State, Long> threadCountsByState = threadCountsByStateThreadLocal.get();
        Long count = threadCountsByState.remove(state);

        // This means that this state was already queried. Assuming that a Gauge will be
        // queried once per publication, this should mean that we need a new snapshot.
        // This always happens if a MeterFilter denies a Gauge that tracks a state.
        if (count == null) {
            threadCountsByStateThreadLocal.remove();
            threadCountsByState = threadCountsByStateThreadLocal.get();
            count = threadCountsByState.remove(state);
        }
        // This means that all the states were queried so next time the method is called,
        // we need a new snapshot.
        // This never happens if a MeterFilter denies a Gauge that tracks a state.
        if (threadCountsByState.isEmpty()) {
            threadCountsByStateThreadLocal.remove();
        }

        return count;
    }

    private Map<Thread.State, Long> getThreadCountsByStateSnapshot() {
        Map<Thread.State, Long> countByThreadState = Arrays
            .stream(threadBean.getThreadInfo(threadBean.getAllThreadIds()))
            .collect(Collectors.groupingBy(ThreadInfo::getThreadState, () -> new EnumMap<>(Thread.State.class),
                    Collectors.counting()));

        for (Thread.State state : Thread.State.values()) {
            countByThreadState.putIfAbsent(state, 0L);
        }

        return countByThreadState;
    }

}
