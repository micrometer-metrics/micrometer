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
import jdk.jfr.EventSettings;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class for {@link MeterBinder MeterBinders} backed by JFR events.
 *
 * @author Szymon Habrainski
 * @since 1.17.0
 */
public abstract class JfrMeterBinder implements MeterBinder, Closeable {

    private final RecordingConfig recordingConfig;

    private final RecordingStream recordingStream;

    protected JfrMeterBinder() {
        this(new RecordingConfig());
    }

    protected JfrMeterBinder(RecordingConfig recordingConfig) {
        this.recordingConfig = Objects.requireNonNull(recordingConfig, "recordingConfig parameter must not be null");
        recordingStream = new RecordingStream();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry parameter must not be null");

        final DefaultEventHandlerRegistrar eventRegistrar = new DefaultEventHandlerRegistrar();
        bindTo(registry, eventRegistrar);
        configure(recordingStream, eventRegistrar.registrations());

        recordingStream.startAsync();
    }

    /**
     * The same as {@link MeterBinder#bindTo(MeterRegistry)}, but with an additional
     * {@link EventHandlerRegistrar} that can be used to register handlers for JFR events.
     * @param registry the registry to bind to
     * @param eventHandlerRegistrar the registrar to use for registering handlers for JFR
     * events
     */
    protected abstract void bindTo(MeterRegistry registry, EventHandlerRegistrar eventHandlerRegistrar);

    private void configure(RecordingStream recordingStream, Map<String, EventRegistration> eventRegistrations) {
        recordingStream.setMaxAge(recordingConfig.maxAge());
        recordingStream.setMaxSize(recordingConfig.maxSizeBytes());

        for (Entry<String, EventRegistration> eventNameAndRegistration : eventRegistrations.entrySet()) {
            final String eventName = eventNameAndRegistration.getKey();
            final EventRegistration registration = eventNameAndRegistration.getValue();

            final EventSettings eventSettings = recordingStream.enable(eventName);

            eventSettings.withoutStackTrace();

            if (registration.options().threshold() != null) {
                eventSettings.withThreshold(registration.options().threshold());
            }
            else {
                eventSettings.withoutThreshold();
            }

            recordingStream.onEvent(eventName, registration.handler());
        }
    }

    @Override
    public void close() {
        recordingStream.close();
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

    /**
     * Registers handlers for {@link RecordedEvent}s.
     *
     * @see JfrMeterBinder#bindTo(MeterRegistry, EventHandlerRegistrar)
     */
    public interface EventHandlerRegistrar {

        /**
         * Registers a handler for an JFR event. {@code handler} is invoked for every
         * event that is emitted by JFR that is matching the event name.
         * @param eventName JFR event name (e.g. {@code jdk.VirtualThreadPinned})
         * @param handler handler that is called with a {@link RecordedEvent} emitted by
         * JFR
         *
         * @see RecordingStream#onEvent(String, Consumer)
         */
        void register(String eventName, Consumer<RecordedEvent> handler);

        /**
         * Registers a handler for an JFR event. {@code handler} is invoked for every
         * event that is emitted by JFR that is matching the event name and which
         * {@link RecordedEvent#getDuration() duration} is greater or equal to
         * {@code threshold}.
         * @param eventName JFR event name (e.g. {@code jdk.VirtualThreadPinned})
         * @param handler handler that is called with a {@link RecordedEvent} emitted by
         * JFR
         *
         * @see RecordingStream#onEvent(String, Consumer)
         */
        void register(String eventName, Consumer<RecordedEvent> handler, Duration threshold);

    }

    private record EventRegistrationOptions(@Nullable Duration threshold) {
    }

    private record EventRegistration(Consumer<RecordedEvent> handler, EventRegistrationOptions options) {
    }

    private static class DefaultEventHandlerRegistrar implements EventHandlerRegistrar {

        private final Map<String, EventRegistration> registrations = new HashMap<>();

        @Override
        public void register(String eventName, Consumer<RecordedEvent> handler) {
            register(eventName, handler, null);
        }

        @Override
        public void register(String eventName, Consumer<RecordedEvent> handler, @Nullable Duration threshold) {
            Objects.requireNonNull(eventName, "eventName parameter must not be null");
            Objects.requireNonNull(handler, "handler parameter must not be null");

            registrations.put(eventName, new EventRegistration(handler, new EventRegistrationOptions(threshold)));
        }

        Map<String, EventRegistration> registrations() {
            return registrations;
        }

    }

}
