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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

class JvmSafepointMetricsTests {

    private static final long TOLERANCE_NS = 1_000_000L;

    private static final String SAFEPOINT_LOG_RESOURCE = "/io/micrometer/java17/instrument/binder/jdk/safepoint.log";

    private static final String SAFEPOINT_OPERATION_METER_NAME = "jvm.safepoint.operation";

    private static final String SAFEPOINT_PAUSE_METER_NAME = "jvm.safepoint.pause";

    private static final String SAFEPOINT_SYNCHRONIZATION_METER_NAME = "jvm.safepoint.synchronization";

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void shouldCreateTimersForOneSafepoint() {
        final MockEventStream eventStream = new MockEventStream();
        try (JvmSafepointMetrics metrics = new JvmSafepointMetrics(eventStream,
                new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 100), List.of())) {
            metrics.bindTo(registry);
            assertThat(eventStream.started()).isTrue();

            final Instant start = Instant.EPOCH;
            eventStream.injectSafepointBeginEvent(1, start);
            eventStream.injectSafepointStateSynchronizationEvent(1, Duration.ofNanos(3709));
            eventStream.injectVmOperationEvent(1, "Cleanup", start, Duration.ofNanos(375), true);
            eventStream.injectSafepointEndEvent(1, start.plusNanos(76875));

            assertTimer(registry.find(SAFEPOINT_OPERATION_METER_NAME).tag("operation", "Cleanup").timer(), 1,
                    Duration.ofNanos(375));
            assertTimer(registry.find(SAFEPOINT_SYNCHRONIZATION_METER_NAME).timer(), 1, Duration.ofNanos(3709));
            assertTimer(registry.find(SAFEPOINT_PAUSE_METER_NAME).timer(), 1, Duration.ofNanos(76875));
        }
        assertThat(eventStream.closed()).isTrue();
    }

    @Test
    void shouldCreateTimersFromSafepointLog() {
        final MockEventStream eventStream = new MockEventStream();
        try (JvmSafepointMetrics metrics = new JvmSafepointMetrics(eventStream,
                new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 100), List.of());
                JvmSafepointLog safepointLog = JvmSafepointLog.open(safepointLogFixturePath())) {
            metrics.bindTo(registry);
            assertThat(eventStream.started()).isTrue();

            long safepointId = 1;
            long expectedPauseCount = 0;
            long expectedSynchronizationCount = 0;
            final Map<String, Long> expectedOperationCounts = new HashMap<>();
            final Map<String, Long> expectedOperationTotalNs = new HashMap<>();
            long expectedPauseTotalNs = 0;
            long expectedSynchronizationTotalNs = 0;
            for (JvmSafepointLog.SafepointLogLine line : safepointLog.snapshot().lines()) {
                eventStream.injectSafepointBeginEvent(safepointId, Instant.EPOCH.plusNanos(safepointId));
                eventStream.injectSafepointStateSynchronizationEvent(safepointId, Duration.ofNanos(line.syncNs()));
                eventStream.injectVmOperationEvent(safepointId, line.operation(), Instant.EPOCH.plusNanos(safepointId),
                        Duration.ofNanos(line.atSafepointNs()), true);
                eventStream.injectSafepointEndEvent(safepointId, Instant.EPOCH.plusNanos(safepointId + line.totalNs()));

                expectedPauseCount++;
                expectedSynchronizationCount++;
                expectedOperationCounts.merge(line.operation(), 1L, Long::sum);
                expectedOperationTotalNs.merge(line.operation(), line.atSafepointNs(), Long::sum);
                expectedPauseTotalNs += line.totalNs();
                expectedSynchronizationTotalNs += line.syncNs();

                assertTimer(registry.find(SAFEPOINT_OPERATION_METER_NAME).tag("operation", line.operation()).timer(),
                        expectedOperationCounts.get(line.operation()),
                        Duration.ofNanos(expectedOperationTotalNs.get(line.operation())));
                assertTimer(registry.find(SAFEPOINT_PAUSE_METER_NAME).timer(), expectedPauseCount,
                        Duration.ofNanos(expectedPauseTotalNs));
                assertTimer(registry.find(SAFEPOINT_SYNCHRONIZATION_METER_NAME).timer(), expectedSynchronizationCount,
                        Duration.ofNanos(expectedSynchronizationTotalNs));
                safepointId++;
            }
        }
        assertThat(eventStream.closed()).isTrue();
    }

    @Test
    void shouldCreateTimersFromRealJFRStream() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final RecordingSimpleMeterRegistry recordingRegistry = new RecordingSimpleMeterRegistry();

        try (JvmSafepointLog safepointLog = JvmSafepointLog.open(liveSafepointLogPath());
                final JvmSafepointMetrics metrics = new JvmSafepointMetrics(
                        new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 1024), List.of())) {
            metrics.bindTo(recordingRegistry);
            threadMXBean.findDeadlockedThreads();
            await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(recordingRegistry.find(SAFEPOINT_OPERATION_METER_NAME)
                    .tag("operation", "FindDeadlocks")
                    .timer()).isNotNull());

            final List<TimerRecording> initialRecordings = recordingRegistry.recordings();
            final JvmSafepointLog.Snapshot initialSafepointLogSnapshot = safepointLog.snapshot();
            final List<String> expectedOperations = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                threadMXBean.findDeadlockedThreads();
                expectedOperations.add("FindDeadlocks");
            }
            for (int i = 0; i < 5; i++) {
                threadMXBean.dumpAllThreads(false, false);
                expectedOperations.add("ThreadDump");
            }
            final Set<String> expectedOperationNames = new HashSet<>(expectedOperations);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                final List<JvmSafepointLog.SafepointLogLine> lines = safepointLog.snapshot()
                    .skipped(initialSafepointLogSnapshot)
                    .stream()
                    .filter(line -> expectedOperationNames.contains(line.operation()))
                    .toList();
                assertThat(lines).extracting(JvmSafepointLog.SafepointLogLine::operation)
                    .containsExactlyElementsOf(expectedOperations);

                final List<TimerRecording> recordings = recordingRegistry.recordings();
                final List<TimerRecording> measuredRecordings = recordings.subList(initialRecordings.size(),
                        recordings.size());
                final List<TimerRecording> operations = measuredRecordings.stream()
                    .filter(recording -> SAFEPOINT_OPERATION_METER_NAME.equals(recording.name())
                            && expectedOperationNames.contains(recording.operation()))
                    .toList();

                assertThat(operations).hasSameSizeAs(lines);
                for (int i = 0; i < lines.size(); i++) {
                    final JvmSafepointLog.SafepointLogLine line = lines.get(i);
                    assertThat(operations.get(i).operation()).isEqualTo(line.operation());
                    assertThat(operations.get(i).duration().toNanos()).isCloseTo(line.atSafepointNs(),
                            within(TOLERANCE_NS));
                }

                final Map<String, List<JvmSafepointLog.SafepointLogLine>> linesByOperation = new HashMap<>();
                for (JvmSafepointLog.SafepointLogLine line : lines) {
                    linesByOperation.computeIfAbsent(line.operation(), ignored -> new ArrayList<>()).add(line);
                }
                for (Map.Entry<String, List<JvmSafepointLog.SafepointLogLine>> entry : linesByOperation.entrySet()) {
                    final Timer timer = recordingRegistry.find(SAFEPOINT_OPERATION_METER_NAME)
                        .tag("operation", entry.getKey())
                        .timer();
                    final List<TimerRecording> initialOperationRecordings = initialRecordings.stream()
                        .filter(recording -> SAFEPOINT_OPERATION_METER_NAME.equals(recording.name())
                                && entry.getKey().equals(recording.operation()))
                        .toList();
                    final long initialTotalNs = initialOperationRecordings.stream()
                        .mapToLong(recording -> recording.duration().toNanos())
                        .sum();
                    final long expectedTotalNs = entry.getValue()
                        .stream()
                        .mapToLong(JvmSafepointLog.SafepointLogLine::atSafepointNs)
                        .sum();
                    assertThat(timer).isNotNull();
                    assertThat(timer.count() - initialOperationRecordings.size()).isEqualTo(entry.getValue().size());
                    assertThat(timer.totalTime(TimeUnit.NANOSECONDS) - initialTotalNs).isCloseTo(expectedTotalNs,
                            within((double) TOLERANCE_NS * entry.getValue().size()));
                }
            });
        }
    }

    @Test
    void shouldNotCreateTimerForSafepointWithDroppedBeginEvent() {
        final MockEventStream eventStream = new MockEventStream();
        try (JvmSafepointMetrics metrics = new JvmSafepointMetrics(eventStream,
                new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 100), List.of())) {
            metrics.bindTo(registry);
            assertThat(eventStream.started()).isTrue();

            final Instant start = Instant.EPOCH;
            eventStream.injectSafepointStateSynchronizationEvent(1, Duration.ofNanos(750));
            eventStream.injectVmOperationEvent(1, "Cleanup", start, Duration.ofNanos(916), true);
            eventStream.injectSafepointEndEvent(1, start.plusNanos(2333));

            assertTimer(registry.find(SAFEPOINT_OPERATION_METER_NAME).tag("operation", "Cleanup").timer(), 1,
                    Duration.ofNanos(916));
            assertTimer(registry.find(SAFEPOINT_SYNCHRONIZATION_METER_NAME).timer(), 1, Duration.ofNanos(750));
            assertThat(registry.find(SAFEPOINT_PAUSE_METER_NAME).timer()).isNull();
        }
        assertThat(eventStream.closed()).isTrue();
    }

    @Test
    void shouldEvictOldEntriesFromCacheOnOverflow() {
        final MockEventStream eventStream = new MockEventStream();
        try (JvmSafepointMetrics metrics = new JvmSafepointMetrics(eventStream,
                new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 2), List.of())) {
            metrics.bindTo(registry);
            assertThat(eventStream.started()).isTrue();

            final Instant start = Instant.EPOCH;
            eventStream.injectSafepointBeginEvent(1, start);
            eventStream.injectSafepointBeginEvent(2, start.plusMillis(1));
            eventStream.injectSafepointBeginEvent(3, start.plusMillis(2));

            eventStream.injectSafepointEndEvent(1, start.plusMillis(10));
            assertThat(registry.find(SAFEPOINT_PAUSE_METER_NAME).timer()).isNull();

            eventStream.injectSafepointEndEvent(2, start.plusMillis(11));
            eventStream.injectSafepointEndEvent(3, start.plusMillis(12));

            assertTimer(registry.find(SAFEPOINT_PAUSE_METER_NAME).timer(), 2, Duration.ofMillis(20));
            assertThat(registry.find(SAFEPOINT_PAUSE_METER_NAME).timers()).hasSize(1);
        }
        assertThat(eventStream.closed()).isTrue();
    }

    @Test
    void shouldNotCreateTimerForNonVmOperationEvent() {
        final MockEventStream eventStream = new MockEventStream();
        try (JvmSafepointMetrics metrics = new JvmSafepointMetrics(eventStream,
                new JvmSafepointMetrics.RecordingConfig(Duration.ofSeconds(10), 1024, 100), List.of())) {
            metrics.bindTo(registry);
            assertThat(eventStream.started()).isTrue();

            eventStream.injectVmOperationEvent(1, "HandshakeOneThread", Instant.now(), Duration.ofMillis(1), false);

            assertThat(registry.find(SAFEPOINT_OPERATION_METER_NAME).timer()).isNull();
        }
        assertThat(eventStream.closed()).isTrue();
    }

    private static void assertTimer(Timer timer, long count, Duration duration) {
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(count);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(duration.toNanos(), within((double) TOLERANCE_NS));
    }

    private static Path liveSafepointLogPath() {
        return Path.of(Objects.requireNonNull(System.getProperty("micrometer.test.safepoint.log.path"),
                "micrometer.test.safepoint.log.path system property must be set"));
    }

    private static Path safepointLogFixturePath() {
        try {
            return Path
                .of(Objects.requireNonNull(JvmSafepointMetricsTests.class.getResource(SAFEPOINT_LOG_RESOURCE)).toURI());
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to load safepoint log fixture", e);
        }
    }

    private record TimerRecording(String name, String operation, Duration duration) {
    }

    private static final class RecordingSimpleMeterRegistry extends SimpleMeterRegistry {

        private final List<TimerRecording> recordings = new CopyOnWriteArrayList<>();

        private RecordingSimpleMeterRegistry() {
            super(SimpleConfig.DEFAULT, new MockClock());
        }

        @Override
        protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                PauseDetector pauseDetector) {
            return new CumulativeTimer(id, clock, distributionStatisticConfig, pauseDetector, TimeUnit.NANOSECONDS) {
                @Override
                protected void recordNonNegative(long amount, TimeUnit unit) {
                    super.recordNonNegative(amount, unit);
                    recordings.add(new TimerRecording(id.getName(), Objects.toString(id.getTag("operation"), ""),
                            Duration.ofNanos(unit.toNanos(amount))));
                }
            };
        }

        List<TimerRecording> recordings() {
            return List.copyOf(recordings);
        }

    }

}
