/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.java17.instrument.binder.jdk;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;

/**
 * Metrics instrumentation for JVM safepoints.
 *
 * <p>
 * Tracks the duration of safepoint pauses ({@code jvm.safepoint.pause}), the time to
 * bring all threads to a safepoint-safe state ({@code jvm.safepoint.synchronization}),
 * and the duration of individual VM operations executed at a safepoint
 * ({@code jvm.safepoint.operation}, tagged by operation name).
 *
 * @author Szymon Habrainski
 * @see <a href="https://openjdk.org/jeps/328">JEP 328: Flight Recorder</a>
 * @since 1.14.0
 */
public class JvmSafepointMetrics implements MeterBinder, Closeable {
    private static final String JFR_EVENT_SAFEPOINT_BEGIN = "jdk.SafepointBegin";
    private static final String JFR_EVENT_SAFEPOINT_END = "jdk.SafepointEnd";
    private static final String JFR_EVENT_SAFEPOINT_STATE_SYNCHRONIZATION = "jdk.SafepointStateSynchronization";

    private static final String JFR_EVENT_EXECUTE_VM_OPERATION = "jdk.ExecuteVMOperation";

    private static final String METER_NAME_PREFIX = "jvm.safepoint.";

    private final Iterable<Tag> tags;
    private final RecordingStream recordingStream;
    // Stores all begin times of not-yet-ended safepoints.
    private final Map<Long, Instant> safepointBeginCache;

    public JvmSafepointMetrics() {
        this(emptyList());
    }

    public JvmSafepointMetrics(Iterable<Tag> tags) {
        this(new RecordingConfig(), tags);
    }

    public JvmSafepointMetrics(RecordingConfig config, Iterable<Tag> tags) {
        this(configureRecordingStream(new RecordingStream(), config), config, tags);
    }

    JvmSafepointMetrics(RecordingStream recordingStream, RecordingConfig config, Iterable<Tag> tags) {
        this.recordingStream = Objects.requireNonNull(recordingStream,
                                                      "recordingStream parameter must not be null");
        Objects.requireNonNull(config, "config parameter must not be null");
        this.tags = Objects.requireNonNull(tags, "tags parameter must not be null");

        safepointBeginCache = new LinkedHashMap<>(config.maxCacheSize()) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Instant> eldest) {
                return size() > config.maxCacheSize();
            }
        };
    }

    private static RecordingStream configureRecordingStream(RecordingStream recordingStream,
                                                            RecordingConfig config) {
        recordingStream.enable(JFR_EVENT_SAFEPOINT_BEGIN)
                       .withoutStackTrace()
                       .withoutThreshold();
        recordingStream.enable(JFR_EVENT_SAFEPOINT_END)
                       .withoutStackTrace()
                       .withoutThreshold();
        recordingStream.enable(JFR_EVENT_EXECUTE_VM_OPERATION)
                       .withoutStackTrace()
                       .withoutThreshold();
        recordingStream.enable(JFR_EVENT_SAFEPOINT_STATE_SYNCHRONIZATION)
                       .withoutStackTrace()
                       .withoutThreshold();

        recordingStream.setMaxAge(config.maxAge());
        recordingStream.setMaxSize(config.maxSizeBytes());
        recordingStream.startAsync();
        return recordingStream;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        recordingStream.onEvent(JFR_EVENT_SAFEPOINT_BEGIN, this::handleSafepointBegin);
        recordingStream.onEvent(JFR_EVENT_EXECUTE_VM_OPERATION,
                                event -> handleExecuteVmOperation(event, registry));
        recordingStream.onEvent(JFR_EVENT_SAFEPOINT_END, event -> handleSafepointEnd(event, registry));
        recordingStream.onEvent(JFR_EVENT_SAFEPOINT_STATE_SYNCHRONIZATION,
                                event -> handleSafepointStateSynchronization(event, registry));
    }

    private void handleSafepointBegin(RecordedEvent event) {
        safepointBeginCache.put(event.getLong("safepointId"), event.getStartTime());
    }

    private void handleExecuteVmOperation(RecordedEvent event, MeterRegistry registry) {
        // We are only interested in VM operations done at a safepoint/ oper
        // With that we are filtering out VM operations like thread-local handshakes.
        if (event.getBoolean("safepoint")) {
            Timer.builder(METER_NAME_PREFIX + "operation")
                 .description("Duration of VM operations executed at a safepoint")
                 .tags(tags)
                 .tag("operation", event.getString("operation"))
                 .register(registry)
                 .record(event.getDuration());
        } else {
            System.out.println(event);
        }
    }

    private void handleSafepointStateSynchronization(RecordedEvent event, MeterRegistry registry) {
        Timer.builder(METER_NAME_PREFIX + "synchronization")
             .description("Time to bring all threads to a safepoint-safe state")
             .tags(tags)
             .register(registry)
             .record(event.getDuration());
    }

    private void handleSafepointEnd(RecordedEvent event, MeterRegistry registry) {
        final Instant beginTime = safepointBeginCache.remove(event.getLong("safepointId"));
        if (beginTime != null) {
            Timer.builder(METER_NAME_PREFIX + "pause")
                 .description("Duration of safepoint pauses")
                 .tags(tags)
                 .register(registry)
                 .record(Duration.between(beginTime, event.getEndTime()));
        }
    }

    @Override
    public void close() {
        recordingStream.close();
        safepointBeginCache.clear();
    }

    public record RecordingConfig(Duration maxAge, long maxSizeBytes, int maxCacheSize) {
        public RecordingConfig() {
            this(Duration.ofSeconds(5), 10L * 1024 * 1024, 1024);
        }

        public RecordingConfig {
            Objects.requireNonNull(maxAge, "maxAge parameter must not be null");
            if (maxSizeBytes < 1) {
                throw new IllegalArgumentException("maxSizeBytes must be positive");
            }
            if (maxCacheSize < 1) {
                throw new IllegalArgumentException("maxCacheSize must be positive");
            }
        }
    }

}
