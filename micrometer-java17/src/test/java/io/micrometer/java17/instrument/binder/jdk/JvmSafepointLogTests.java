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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JvmSafepointLogTests {

    private static final String SAFEPOINT_LOG_RESOURCE = "/io/micrometer/java17/instrument/binder/jdk/safepoint.log";

    @Test
    void shouldHandleEmptyFile() throws IOException {
        final Path file = Files.createTempFile("safepoint", ".log");

        try (JvmSafepointLog log = JvmSafepointLog.open(file)) {
            final JvmSafepointLog.Snapshot snapshot = log.snapshot();

            assertThat(snapshot.lineCount()).isZero();
            assertThat(snapshot.lines()).isEmpty();
        }
    }

    @Test
    void shouldParseSingleSafepointLine() throws IOException {
        final Path file = Files.createTempFile("safepoint", ".log");
        Files.writeString(file,
                "[0.600s][info][safepoint] Safepoint \"JFRCheckpoint\", Time since last: 20753625 ns, Reaching safepoint: 32834 ns, Cleanup: 51375 ns, At safepoint: 9250 ns, Total: 93459 ns",
                StandardCharsets.UTF_8);

        try (JvmSafepointLog log = JvmSafepointLog.open(file)) {
            final JvmSafepointLog.Snapshot snapshot = log.snapshot();

            assertThat(snapshot.lineCount()).isEqualTo(1);
            assertThat(snapshot.lines())
                .containsExactly(new JvmSafepointLog.SafepointLogLine(1, "JFRCheckpoint", 32834, 9250, 93459));
        }
    }

    @Test
    void shouldThrowOnBrokenLines() throws IOException {
        final Path file = Files.createTempFile("safepoint", ".log");
        Files.writeString(file,
                """
                        this is not a safepoint line
                        [0.600s][info][safepoint] Safepoint "JFRCheckpoint", Time since last: 20753625 ns, Reaching safepoint: 32834 ns, Cleanup: 51375 ns, At safepoint: 9250 ns, Total: 93459 ns
                        """,
                StandardCharsets.UTF_8);

        try (JvmSafepointLog log = JvmSafepointLog.open(file)) {
            assertThatThrownBy(log::snapshot).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized safepoint log line 1");
        }
    }

    @Test
    void shouldParseSafepointLog() {
        try (JvmSafepointLog log = JvmSafepointLog.open(safepointLogFixturePath())) {
            final JvmSafepointLog.Snapshot snapshot = log.snapshot();

            assertThat(snapshot.lineCount()).isEqualTo(27);
            assertThat(snapshot.lines()).hasSize(27);
            assertThat(snapshot.lines().get(0))
                .isEqualTo(new JvmSafepointLog.SafepointLogLine(1, "ICBufferFull", 3709, 375, 76875));
            assertThat(snapshot.lines().get(13))
                .isEqualTo(new JvmSafepointLog.SafepointLogLine(14, "FindDeadlocks", 750, 916, 2333));
            assertThat(snapshot.lines().get(26))
                .isEqualTo(new JvmSafepointLog.SafepointLogLine(27, "JFRCheckpoint", 3625, 14042, 86375));
        }
    }

    @Test
    void skippedShouldReturnLinesAfterPreviousSnapshotLineCount() {
        try (JvmSafepointLog log = JvmSafepointLog.open(safepointLogFixturePath())) {
            final JvmSafepointLog.Snapshot snapshot = log.snapshot();

            assertThat(snapshot.skipped(new JvmSafepointLog.Snapshot(10, List.of()))).hasSize(17)
                .first()
                .isEqualTo(new JvmSafepointLog.SafepointLogLine(11, "FindDeadlocks", 37042, 8875, 75542));
        }
    }

    private static Path safepointLogFixturePath() {
        try {
            return Path
                .of(Objects.requireNonNull(JvmSafepointLogTests.class.getResource(SAFEPOINT_LOG_RESOURCE)).toURI());
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to load safepoint log fixture", e);
        }
    }

}
