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
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

import java.io.Closeable;
import java.time.Duration;
import java.util.Objects;

/**
 * Base class for {@link MeterBinder MeterBinders} backed by a JFR {@link EventStream}.
 *
 * @author Szymon Habrainski
 * @since 1.17.0
 */
public abstract class JfrMeterBinder implements MeterBinder, Closeable {

    private final EventStream eventStream;

    protected JfrMeterBinder(EventStream eventStream) {
        this.eventStream = Objects.requireNonNull(eventStream, "eventStream parameter must not be null");
    }

    protected static RecordingStream createRecordingStream(RecordingConfig config) {
        Objects.requireNonNull(config, "config parameter must not be null");
        RecordingStream recordingStream = new RecordingStream();
        recordingStream.setMaxAge(config.maxAge());
        recordingStream.setMaxSize(config.maxSizeBytes());
        return recordingStream;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, eventStream);
        eventStream.startAsync();
    }

    protected abstract void register(MeterRegistry registry, EventStream eventStream);

    @Override
    public void close() {
        eventStream.close();
    }

    public record RecordingConfig(Duration maxAge, long maxSizeBytes) {
        public RecordingConfig() {
            this(Duration.ofSeconds(5), 10L * 1024 * 1024);
        }

        public RecordingConfig {
            Objects.requireNonNull(maxAge, "maxAge parameter must not be null");
            if (maxSizeBytes < 1) {
                throw new IllegalArgumentException("maxSizeBytes must be positive");
            }
        }
    }

}
