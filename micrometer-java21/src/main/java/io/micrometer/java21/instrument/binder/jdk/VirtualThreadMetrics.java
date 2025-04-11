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
package io.micrometer.java21.instrument.binder.jdk;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.time.Duration;
import java.util.Objects;

import static java.util.Collections.emptyList;

/**
 * Metrics instrumentation for Java Virtual Threads.
 *
 * @author Artyom Gabeev
 * @see <a href="https://openjdk.org/jeps/425#JDK-Flight-Recorder-JFR">JEP 425</a>
 * @see VirtualThreadSchedulerMXBean
 * @since 1.14.0
 */
public class VirtualThreadMetrics implements MeterBinder, Closeable {

    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";

    private static final String SUBMIT_FAILED_EVENT = "jdk.VirtualThreadSubmitFailed";

    private static final String LIVE_THREADS_DESCRIPTION = "Approximate current number of virtual threads that are unfinished";

    private static final String METER_NAME_PREFIX = "jvm.threads.virtual.";

    private final RecordingStream recordingStream;

    private final Iterable<Tag> tags;

    public VirtualThreadMetrics() {
        this(new RecordingConfig(), emptyList());
    }

    public VirtualThreadMetrics(Iterable<Tag> tags) {
        this(new RecordingConfig(), tags);
    }

    private VirtualThreadMetrics(RecordingConfig config, Iterable<Tag> tags) {
        this.recordingStream = createRecordingStream(config);
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Timer pinnedTimer = Timer.builder(METER_NAME_PREFIX + "pinned")
            .description("The duration while the virtual thread was pinned without releasing its platform thread")
            .tags(tags)
            .register(registry);

        Counter submitFailedCounter = Counter.builder(METER_NAME_PREFIX + "submit.failed")
            .description("The number of events when starting or unparking a virtual thread failed")
            .tags(tags)
            .register(registry);

        recordingStream.onEvent(PINNED_EVENT, event -> pinnedTimer.record(event.getDuration()));
        recordingStream.onEvent(SUBMIT_FAILED_EVENT, event -> submitFailedCounter.increment());

        bindVirtualThreadSchedulerMXBean(registry);
    }

    private void bindVirtualThreadSchedulerMXBean(MeterRegistry registry) {
        try {
            Class clazz = Class.forName("jdk.management.VirtualThreadSchedulerMXBean");
            PlatformManagedObject platformMXBean = ManagementFactory.getPlatformMXBean(clazz);
            Object ignored = clazz.cast(platformMXBean);

            MethodHandle getParallelism = MethodHandles.publicLookup()
                .findVirtual(clazz, "getParallelism", MethodType.methodType(int.class));
            MethodHandle getPoolSize = MethodHandles.publicLookup()
                .findVirtual(clazz, "getPoolSize", MethodType.methodType(int.class));
            MethodHandle getMountedVirtualThreadCount = MethodHandles.publicLookup()
                .findVirtual(clazz, "getMountedVirtualThreadCount", MethodType.methodType(int.class));
            MethodHandle getQueuedVirtualThreadCount = MethodHandles.publicLookup()
                .findVirtual(clazz, "getQueuedVirtualThreadCount", MethodType.methodType(long.class));

            Gauge.builder(METER_NAME_PREFIX + "parallelism", platformMXBean, o -> invoke(getParallelism, o))
                .description("Virtual thread scheduler's target parallelism")
                .register(registry);

            Gauge.builder(METER_NAME_PREFIX + "pool.size", platformMXBean, o -> invoke(getPoolSize, o))
                .baseUnit(BaseUnits.THREADS)
                .description(
                        "Current number of platform threads that the scheduler has started but have not terminated; -1 if not known.")
                .register(registry);

            Gauge.builder(METER_NAME_PREFIX + "live", platformMXBean, o -> invoke(getMountedVirtualThreadCount, o))
                .tag("scheduling.status", "mounted")
                .baseUnit(BaseUnits.THREADS)
                .description(LIVE_THREADS_DESCRIPTION)
                .register(registry);

            Gauge.builder(METER_NAME_PREFIX + "live", platformMXBean, o -> invoke(getQueuedVirtualThreadCount, o))
                .tag("scheduling.status", "queued")
                .baseUnit(BaseUnits.THREADS)
                .description(LIVE_THREADS_DESCRIPTION)
                .register(registry);
        }
        catch (ClassNotFoundException | ClassCastException | NoSuchMethodException | IllegalAccessException ignored) {
            // cannot instrument VirtualThreadSchedulerMXBean
        }
    }

    private <T> double invoke(MethodHandle methodHandle, T arg) {
        try {
            return (double) methodHandle.invoke(arg);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private RecordingStream createRecordingStream(RecordingConfig config) {
        RecordingStream recordingStream = new RecordingStream();
        recordingStream.enable(PINNED_EVENT).withThreshold(config.pinnedThreshold);
        recordingStream.enable(SUBMIT_FAILED_EVENT);
        recordingStream.setMaxAge(config.maxAge);
        recordingStream.setMaxSize(config.maxSizeBytes);
        recordingStream.startAsync();

        return recordingStream;
    }

    @Override
    public void close() {
        recordingStream.close();
    }

    private record RecordingConfig(Duration maxAge, long maxSizeBytes, Duration pinnedThreshold) {
        private RecordingConfig() {
            this(Duration.ofSeconds(5), 10L * 1024 * 1024, Duration.ofMillis(20));
        }

        private RecordingConfig {
            Objects.requireNonNull(maxAge, "maxAge parameter must not be null");
            Objects.requireNonNull(pinnedThreshold, "pinnedThreshold must not be null");
            if (maxSizeBytes < 0) {
                throw new IllegalArgumentException("maxSizeBytes must be positive");
            }
        }
    }

}
