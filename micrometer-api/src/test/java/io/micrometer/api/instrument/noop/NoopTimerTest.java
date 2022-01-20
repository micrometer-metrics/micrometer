/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.api.instrument.noop;

import io.micrometer.api.instrument.Meter.Id;
import io.micrometer.api.instrument.Meter.Type;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.distribution.HistogramSnapshot;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link NoopTimer}.
 *
 * @author Oleksii Bondar
 */
class NoopTimerTest {

    private Id id = new Id("test", Tags.of("name", "value"), "", "", Type.TIMER);
    private NoopTimer timer = new NoopTimer(id);

    @Test
    void returnsId() {
        assertThat(timer.getId()).isEqualTo(id);
    }

    @Test
    void recordSupplier() {
        String expectedResult = "value";
        Supplier<String> supplier = () -> expectedResult;
        assertThat(timer.record(supplier)).isEqualTo(expectedResult);
    }

    @Test
    void recordCallable() throws Exception {
        String expectedResult = "value";
        Callable<String> callable = () -> expectedResult;
        assertThat(timer.recordCallable(callable)).isEqualTo(expectedResult);
    }

    @Test
    void recordRunnable() {
        Runnable runnable = mock(Runnable.class);
        timer.record(runnable);
        verify(runnable).run();
    }

    @Test
    void returnsCountAsZero() {
        assertThat(timer.count()).isEqualTo(0L);
    }

    @Test
    void returnsTotalTimeAsZero() {
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(0L);
    }

    @Test
    void returnsMaxAsZero() {
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0L);
    }

    @Test
    void returnsBaseTimeUnit() {
        assertThat(timer.baseTimeUnit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void returnsEmptySnapshot() {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        HistogramSnapshot expectedHistogram = HistogramSnapshot.empty(0, 0, 0);
        assertThat(snapshot.count()).isEqualTo(expectedHistogram.count());
        assertThat(snapshot.total()).isEqualTo(expectedHistogram.total());
        assertThat(snapshot.max()).isEqualTo(expectedHistogram.max());
    }
}
