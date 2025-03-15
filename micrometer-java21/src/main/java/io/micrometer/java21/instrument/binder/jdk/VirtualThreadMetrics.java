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
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Collections.emptyList;

/**
 * Instrumentation support for Virtual Threads, see:
 * https://openjdk.org/jeps/425#JDK-Flight-Recorder-JFR
 *
 * @author Artyom Gabeev
 * @since 1.14.0
 */
public class VirtualThreadMetrics implements MeterBinder, Closeable {

    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";

    private static final String SUBMIT_FAILED_EVENT = "jdk.VirtualThreadSubmitFailed";

    private static final String START_EVENT = "jdk.VirtualThreadStart";

    private static final String END_EVENT = "jdk.VirtualThreadEnd";

    private static final String SUBMIT_FAILED_METRIC_NAME = "jvm.threads.virtual.submit.failed";

    private static final String VT_PINNED_METRIC_NAME = "jvm.threads.virtual.pinned";

    private static final String VT_ACTIVE_METRIC_NAME = "jvm.threads.virtual.active";

    private final RecordingConfig recordingCfg;

    private RecordingStream recordingStream;

    private final Iterable<Tag> tags;

    /**
     * Instantiates a new Virtual thread metrics.
     */
    public VirtualThreadMetrics() {
        this(new RecordingConfig(), emptyList());
    }

    /**
     * Instantiates a new Virtual thread metrics.
     * @param config the config
     */
    public VirtualThreadMetrics(RecordingConfig config) {
        this(config, emptyList());
    }

    /**
     * Instantiates a new Virtual thread metrics.
     * @param tags the tags
     */
    public VirtualThreadMetrics(Iterable<Tag> tags) {
        this(new RecordingConfig(), tags);
    }

    /**
     * Instantiates a new Virtual thread metrics.
     * @param config the config
     * @param tags the tags
     */
    public VirtualThreadMetrics(RecordingConfig config, Iterable<Tag> tags) {
        this.recordingCfg = config;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (this.recordingStream == null) {
            this.recordingStream = createRecordingStream(this.recordingCfg);
        }

        if (recordingCfg.pinnedMetricEnabled) {
            Timer pinnedTimer = Timer.builder(VT_PINNED_METRIC_NAME)
                .description("The duration while the virtual thread was pinned without releasing its platform thread")
                .tags(tags)
                .register(registry);

            recordingStream.onEvent(PINNED_EVENT, event -> pinnedTimer.record(event.getDuration()));
        }

        if (recordingCfg.submitFailedMetricEnabled) {
            Counter submitFailedCounter = Counter.builder(SUBMIT_FAILED_METRIC_NAME)
                .description("The number of events when starting or unparking a virtual thread failed")
                .tags(tags)
                .register(registry);

            recordingStream.onEvent(SUBMIT_FAILED_EVENT, event -> submitFailedCounter.increment());
        }

        if (recordingCfg.activeMetricEnabled) {
            final LongAdder activeCounter = new LongAdder();
            this.recordingStream.onEvent(START_EVENT, event -> activeCounter.increment());
            this.recordingStream.onEvent(END_EVENT, event -> activeCounter.decrement());

            Gauge.builder(VT_ACTIVE_METRIC_NAME, activeCounter::doubleValue)
                .description("The number of active virtual threads")
                .tags(tags)
                .register(registry);
        }
    }

    private RecordingStream createRecordingStream(RecordingConfig config) {
        RecordingStream recordingStream = new RecordingStream();
        if (config.pinnedMetricEnabled) {
            recordingStream.enable(PINNED_EVENT).withThreshold(config.pinnedThreshold);
        }
        if (config.submitFailedMetricEnabled) {
            recordingStream.enable(SUBMIT_FAILED_EVENT);
        }
        if (config.activeMetricEnabled) {
            recordingStream.enable(START_EVENT);
            recordingStream.enable(END_EVENT);
        }
        recordingStream.setMaxAge(config.maxAge);
        recordingStream.setMaxSize(config.maxSizeBytes);
        recordingStream.startAsync();

        return recordingStream;
    }

    @Override
    public void close() {
        recordingStream.close();
    }

    /**
     * The RecordingConfig type allows you to configure the recording features and the
     * enabled events to listen to.
     */
    public record RecordingConfig(Duration maxAge, long maxSizeBytes, Duration pinnedThreshold,
            boolean pinnedMetricEnabled, boolean submitFailedMetricEnabled, boolean activeMetricEnabled) {

        public RecordingConfig() {
            this(true, true, false);
        }

        public RecordingConfig(boolean pinnedMetricEnabled, boolean submitFailedMetricEnabled,
                boolean activeMetricEnabled) {
            this(Duration.ofSeconds(5), 10L * 1024 * 1024, Duration.ofMillis(20), pinnedMetricEnabled,
                    submitFailedMetricEnabled, activeMetricEnabled);
        }

        /**
         * Instantiates a new Recording config.
         * @param maxAge the max age
         * @param maxSizeBytes the max size bytes
         * @param pinnedThreshold the pinned threshold
         * @param pinnedMetricEnabled the pinned metric enabled
         * @param submitFailedMetricEnabled the submit failed metric enabled
         * @param activeMetricEnabled the active metric enabled
         */
        public RecordingConfig {
            Objects.requireNonNull(maxAge, "maxAge parameter must not be null");
            Objects.requireNonNull(pinnedThreshold, "pinnedThreshold must not be null");
            if (maxSizeBytes < 0) {
                throw new IllegalArgumentException("maxSizeBytes must be positive");
            }
        }
    }

}
