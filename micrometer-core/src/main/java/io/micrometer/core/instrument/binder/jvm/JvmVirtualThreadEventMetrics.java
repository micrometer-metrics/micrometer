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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

import static java.util.Collections.emptyList;

public class JvmVirtualThreadEventMetrics implements MeterBinder, Closeable {

    private static final String PINNED_EVENT = "jdk.VirtualThreadPinned";

    private static final String SUBMIT_FAILED_EVENT = "jdk.VirtualThreadSubmitFailed";

    private final Iterable<Tag> tags;

    private final RecordingStream recordingStream;

    public JvmVirtualThreadEventMetrics() {
        this(emptyList());
    }

    public JvmVirtualThreadEventMetrics(Iterable<Tag> tags) {
        this.tags = tags;
        this.recordingStream = new RecordingStream();

        recordingStream.enable(PINNED_EVENT);
        recordingStream.enable(SUBMIT_FAILED_EVENT);
        recordingStream.setMaxAge(Duration.ofSeconds(5));
        recordingStream.startAsync();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        final Timer pinnedTimer =
            Timer.builder("jvm.virtual.thread.pinned")
                .tags(tags)
                .description("The duration of virtual threads that were pinned to a physical thread")
                .register(registry);

        final Counter submitFailedCounter =
            Counter.builder("jvm.virtual.thread.submit.failed")
                .tags(tags)
                .description("The number of virtual thread submissions that failed")
                .register(registry);

        recordingStream.onEvent(PINNED_EVENT, event -> pinnedTimer.record(event.getDuration()));
        recordingStream.onEvent(SUBMIT_FAILED_EVENT, event -> submitFailedCounter.increment());
    }

    @Override
    public void close() throws IOException {
        recordingStream.close();
    }
}
