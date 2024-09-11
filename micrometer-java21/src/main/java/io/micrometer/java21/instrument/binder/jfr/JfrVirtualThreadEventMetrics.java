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
package io.micrometer.java21.instrument.binder.jfr;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import static java.util.Collections.emptyList;

public class JfrVirtualThreadEventMetrics implements MeterBinder, Closeable {

    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";

    private static final String SUBMIT_FAILED_EVENT = "jdk.VirtualThreadSubmitFailed";

    private final RecordingSettings settings;

    private final Iterable<Tag> tags;

    private boolean started = false;

    private RecordingStream recordingStream;

    public JfrVirtualThreadEventMetrics() {
        this(new RecordingSettings(), emptyList());
    }

    public JfrVirtualThreadEventMetrics(Iterable<Tag> tags) {
        this(new RecordingSettings(), tags);
    }

    public JfrVirtualThreadEventMetrics(RecordingSettings settings, Iterable<Tag> tags) {
        this.settings = settings;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (started) {
            return;
        }

        started = true;
        recordingStream = createRecordingStream(settings);

        final Timer pinnedTimer = Timer.builder("jvm.virtual.thread.pinned")
            .tags(tags)
            .description("The duration of virtual threads that were pinned to a physical thread")
            .register(registry);

        final Counter submitFailedCounter = Counter.builder("jvm.virtual.thread.submit.failed")
            .tags(tags)
            .description("The number of virtual thread submissions that failed")
            .register(registry);

        recordingStream.onEvent(PINNED_EVENT, event -> pinnedTimer.record(event.getDuration()));
        recordingStream.onEvent(SUBMIT_FAILED_EVENT, event -> submitFailedCounter.increment());
    }

    protected RecordingStream createRecordingStream(RecordingSettings settings) {
        final RecordingStream recordingStream = new RecordingStream();
        recordingStream.enable(PINNED_EVENT).withThreshold(settings.pinnedThreshold);
        recordingStream.enable(SUBMIT_FAILED_EVENT);
        recordingStream.setMaxAge(settings.maxAge);
        recordingStream.setMaxSize(settings.maxSizeBytes);
        recordingStream.startAsync();
        return recordingStream;
    }

    @Override
    public void close() throws IOException {
        if (started) {
            recordingStream.close();
        }
    }

    public record RecordingSettings(Duration maxAge, long maxSizeBytes, Duration pinnedThreshold) {
        public RecordingSettings {
            Objects.requireNonNull(maxAge, "maxAge parameter must not be null");
            Objects.requireNonNull(pinnedThreshold, "pinnedThreshold must not be null");
            if (maxSizeBytes < 0) {
                throw new IllegalArgumentException("maxSizeBytes must be positive");
            }
        }

        public RecordingSettings() {
            this(Duration.ofSeconds(5), 10L * 1024 * 1024, Duration.ofMillis(20));
        }
    }

}
