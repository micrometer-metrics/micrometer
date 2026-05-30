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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads safepoint lines written by the JVM via {@code -Xlog:safepoint:file=...}.
 *
 * <p>
 * Each safepoint produces a line of the form: <pre>
 * [0.600s][info][safepoint      ] Safepoint "JFRCheckpoint", Time since last: 20753625 ns, Reaching safepoint: 32834 ns, Cleanup: 51375 ns, At safepoint: 9250 ns, Total: 93459 ns
 * </pre>
 *
 * <p>
 * Use {@link #snapshot()} before and after the code under test runs, then read
 * {@link Snapshot#skipped(Snapshot)} to obtain only the lines written during the test
 * window.
 */
final class JvmSafepointLog implements AutoCloseable {

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "Safepoint \"([^\"]+)\",.*?Reaching safepoint: (\\d+) ns,.*?At safepoint: (\\d+) ns, Total: (\\d+) ns");

    record SafepointLogLine(int lineNumber, String operation, long syncNs, long atSafepointNs, long totalNs) {
    }

    private final RandomAccessFile file;

    private JvmSafepointLog(Path path) {
        try {
            file = new RandomAccessFile(path.toFile(), "r");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JvmSafepointLog open(Path path) {
        return new JvmSafepointLog(path);
    }

    Snapshot snapshot() {
        final int lineCount = lineCount();
        return new Snapshot(lineCount, readLinesUntil(lineCount));
    }

    private int lineCount() {
        try {
            file.seek(0);
            int lineCount = 0;
            while (file.readLine() != null) {
                lineCount++;
            }
            return lineCount;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<SafepointLogLine> readLinesUntil(int lineCount) {
        final List<SafepointLogLine> lines = new ArrayList<>();
        try {
            file.seek(0);
            for (int i = 1; i <= lineCount; i++) {
                final String line = file.readLine();
                if (line == null) {
                    break;
                }
                final Matcher matcher = LINE_PATTERN.matcher(line);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("Unrecognized safepoint log line " + i + ": " + line);
                }
                lines.add(new SafepointLogLine(i, matcher.group(1), Long.parseLong(matcher.group(2)),
                        Long.parseLong(matcher.group(3)), Long.parseLong(matcher.group(4))));
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(lines);
    }

    @Override
    public void close() {
        try {
            file.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Immutable view of safepoint log lines written up to a specific line count. */
    record Snapshot(int lineCount, List<SafepointLogLine> lines) {
        Snapshot {
            lines = List.copyOf(lines);
        }

        List<SafepointLogLine> skipped(Snapshot previous) {
            return lines.stream().filter(line -> line.lineNumber() > previous.lineCount()).toList();
        }

    }

}
