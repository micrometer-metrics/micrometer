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

import jdk.jfr.EventType;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class MockEventStream implements EventStream {

    private final Map<String, Consumer<RecordedEvent>> eventHandlers = new HashMap<>();

    private boolean started;

    private boolean closed;

    @Override
    public void onEvent(Consumer<RecordedEvent> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> consumer) {
        eventHandlers.put(eventName, consumer);
    }

    @Override
    public void onFlush(Runnable runnable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onError(Consumer<Throwable> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onClose(Runnable runnable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean remove(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReuse(boolean reuse) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOrdered(boolean ordered) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStartTime(Instant instant) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEndTime(Instant instant) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startAsync() {
        started = true;
    }

    @Override
    public void awaitTermination(Duration duration) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    boolean started() {
        return started;
    }

    boolean closed() {
        return closed;
    }

    void injectSafepointBeginEvent(long safepointId, Instant startTime) {
        inject(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_BEGIN, createSafepointBeginEvent(safepointId, startTime));
    }

    void injectSafepointEndEvent(long safepointId, Instant endTime) {
        inject(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_END, createSafepointEndEvent(safepointId, endTime));
    }

    void injectSafepointStateSynchronizationEvent(long safepointId, Duration duration) {
        inject(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_STATE_SYNCHRONIZATION,
                createSafepointStateSynchronizationEvent(safepointId, duration));
    }

    void injectVmOperationEvent(long safepointId, String operation, Instant startTime, Duration duration,
            boolean atSafepoint) {
        inject(JvmSafepointMetrics.JFR_EVENT_EXECUTE_VM_OPERATION,
                createVmOperationEvent(safepointId, operation, startTime, duration, atSafepoint));
    }

    private void inject(String eventName, RecordedEvent event) {
        final Consumer<RecordedEvent> handler = eventHandlers.get(eventName);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for event " + eventName);
        }
        handler.accept(event);
    }

    private static RecordedEvent createSafepointBeginEvent(long safepointId, Instant startTime) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_BEGIN);
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getStartTime()).thenReturn(startTime);
        return event;
    }

    private static RecordedEvent createSafepointEndEvent(long safepointId, Instant endTime) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_END);
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getEndTime()).thenReturn(endTime);
        return event;
    }

    private static RecordedEvent createSafepointStateSynchronizationEvent(long safepointId, Duration duration) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn(JvmSafepointMetrics.JFR_EVENT_SAFEPOINT_STATE_SYNCHRONIZATION);
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getDuration()).thenReturn(duration);
        return event;
    }

    private static RecordedEvent createVmOperationEvent(long safepointId, String operation, Instant startTime,
            Duration duration, boolean atSafepoint) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn(JvmSafepointMetrics.JFR_EVENT_EXECUTE_VM_OPERATION);
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getString("operation")).thenReturn(operation);
        when(event.getBoolean("safepoint")).thenReturn(atSafepoint);
        when(event.getStartTime()).thenReturn(startTime);
        when(event.getDuration()).thenReturn(duration);
        return event;
    }

}
