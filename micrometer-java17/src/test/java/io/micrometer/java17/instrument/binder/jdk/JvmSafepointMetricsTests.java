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

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JvmSafepointMetricsTests {

    private SimpleMeterRegistry registry;

    private static RecordedEvent createSafepointBeginEvent(long safepointId, Instant startTime) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn("jdk.SafepointBegin");
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getStartTime()).thenReturn(startTime);
        return event;
    }

    private static RecordedEvent createSafepointEndEvent(long safepointId, Instant endTime) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn("jdk.SafepointEnd");
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getEndTime()).thenReturn(endTime);
        return event;
    }

    private static RecordedEvent createVmOperationEvent(long safepointId, String operation,
                                                        Instant startTime, Duration duration,
                                                        boolean atSafepoint) {
        final RecordedEvent event = mock(RecordedEvent.class);
        final EventType type = mock(EventType.class);
        when(type.getName()).thenReturn("jdk.ExecuteVMOperation");
        when(event.getEventType()).thenReturn(type);
        when(event.getLong("safepointId")).thenReturn(safepointId);
        when(event.getString("operation")).thenReturn(operation);
        when(event.getBoolean("safepoint")).thenReturn(atSafepoint);
        when(event.getStartTime()).thenReturn(startTime);
        when(event.getDuration()).thenReturn(duration);
        return event;
    }

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void shouldRecordSafepointMetrics() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        try (final JvmSafepointMetrics metrics = new JvmSafepointMetrics()) {
            metrics.bindTo(registry);

            final long deadlockFindingStartNanos = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                threadMXBean.findDeadlockedThreads();
            }
            final long deadlockFindingEndNanos = System.nanoTime();

            final long threadDumpingStartNanos = System.nanoTime();
            for (int i = 0; i < 5; i++) {
                threadMXBean.dumpAllThreads(false, false);
            }
            final long threadDumpingEndNanos = System.nanoTime();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                final Timer findDeadlockTimer = registry.find("jvm.safepoint.operation")
                                                        .tag("operation", "FindDeadlocks")
                                                        .timer();
                assertThat(findDeadlockTimer).isNotNull();
                assertThat(findDeadlockTimer.count()).isEqualTo(10);
                assertThat((long) findDeadlockTimer.totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThan(0).isLessThan(deadlockFindingEndNanos - deadlockFindingStartNanos);

                final Timer dumpTimer = registry.find("jvm.safepoint.operation")
                                                .tag("operation", "ThreadDump")
                                                .timer();
                assertThat(dumpTimer).isNotNull();
                assertThat(dumpTimer.count()).isEqualTo(5);
                assertThat((long) dumpTimer.totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThan(0).isLessThan(threadDumpingEndNanos - threadDumpingStartNanos);

                final Timer pauseTimer = registry.find("jvm.safepoint.pause").timer();
                assertThat(pauseTimer).isNotNull();
                assertThat(pauseTimer.count())
                    .isGreaterThanOrEqualTo(findDeadlockTimer.count() + dumpTimer.count());
                assertThat((long) pauseTimer.totalTime(TimeUnit.NANOSECONDS))
                    .isGreaterThanOrEqualTo((long) (findDeadlockTimer.totalTime(TimeUnit.NANOSECONDS)
                                                    + dumpTimer.totalTime(TimeUnit.NANOSECONDS)));
            });
        }
    }

    @Test
    void shouldRecordSeparatePauseAndOperationMetrics() {
        final EventHandlers handlers = captureHandlers(100);

        handlers.begin().accept(createSafepointBeginEvent(1, Instant.now().minusMillis(10)));
        handlers.vmOp().accept(createVmOperationEvent(1, "Cleanup", Instant.now(), Duration.ofMillis(5), true));
        handlers.end().accept(createSafepointEndEvent(1, Instant.now()));

        final Timer operationTimer = registry.find("jvm.safepoint.operation").tag("operation", "Cleanup")
                                             .timer();
        assertThat(operationTimer).isNotNull();
        assertThat(operationTimer.count()).isEqualTo(1);

        final Timer pauseTimer = registry.find("jvm.safepoint.pause").timer();
        assertThat(pauseTimer).isNotNull();
        assertThat(pauseTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldHandleDroppedBeginEvent() {
        final EventHandlers handlers = captureHandlers(100);

        // SafepointBegin is missing — operation metric should still be recorded
        handlers.vmOp().accept(createVmOperationEvent(1, "Cleanup", Instant.now(), Duration.ofMillis(5), true));
        handlers.end().accept(createSafepointEndEvent(1, Instant.now()));

        assertThat(registry.find("jvm.safepoint.pause").timer()).isNull();

        final Timer operationTimer = registry.find("jvm.safepoint.operation").tag("operation", "Cleanup")
                                             .timer();
        assertThat(operationTimer).isNotNull();
        assertThat(operationTimer.count()).isEqualTo(1);
    }

    @Test
    void shouldEvictOldEntriesFromCache() {
        final EventHandlers handlers = captureHandlers(2);

        handlers.begin().accept(createSafepointBeginEvent(1, Instant.now()));
        handlers.begin().accept(createSafepointBeginEvent(2, Instant.now()));
        handlers.begin().accept(createSafepointBeginEvent(3, Instant.now())); // Should evict safepoint 1

        // Safepoint 1 end should be ignored (evicted from cache)
        handlers.end().accept(createSafepointEndEvent(1, Instant.now().plusMillis(10)));
        assertThat(registry.find("jvm.safepoint.pause").timer()).isNull();

        handlers.end().accept(createSafepointEndEvent(2, Instant.now().plusMillis(10)));
        handlers.end().accept(createSafepointEndEvent(3, Instant.now().plusMillis(10)));

        assertThat(registry.find("jvm.safepoint.pause").timers()).hasSize(1);
        final Timer timer = registry.find("jvm.safepoint.pause").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    void shouldNotRecordNonSafepointVMOperations() {
        final EventHandlers handlers = captureHandlers(100);

        // Simulate a thread-local handshake (safepoint=false)
        handlers.vmOp().accept(createVmOperationEvent(1, "HandshakeOneThread",
                                                      Instant.now(), Duration.ofMillis(1), false));

        assertThat(registry.find("jvm.safepoint.operation").timer()).isNull();
    }

    private EventHandlers captureHandlers(int maxCacheSize) {
        final RecordingStream recordingStream = mock(RecordingStream.class);
        final JvmSafepointMetrics metrics = new JvmSafepointMetrics(recordingStream,
                                                                    new JvmSafepointMetrics.RecordingConfig(
                                                                        Duration.ofSeconds(10), 1024,
                                                                        maxCacheSize),
                                                                    Collections.emptyList());
        metrics.bindTo(registry);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer<RecordedEvent>> beginCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(recordingStream).onEvent(eq("jdk.SafepointBegin"), beginCaptor.capture());

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer<RecordedEvent>> vmOpCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(recordingStream).onEvent(eq("jdk.ExecuteVMOperation"), vmOpCaptor.capture());

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Consumer<RecordedEvent>> endCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(recordingStream).onEvent(eq("jdk.SafepointEnd"), endCaptor.capture());

        return new EventHandlers(beginCaptor.getValue(), vmOpCaptor.getValue(), endCaptor.getValue());
    }

    private record EventHandlers(Consumer<RecordedEvent> begin, Consumer<RecordedEvent> vmOp,
                                 Consumer<RecordedEvent> end) {}
}
