package org.springframework.metrics.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.metrics.instrument.MockClock.clock;

class LongTaskTimerTest {

    @DisplayName("total time is preserved for a single timing")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void testRecord(MeterRegistry collector) {
        LongTaskTimer t = collector.longTaskTimer("myTimer");
        long tId = t.start();
        clock(collector).addAndGetNanos(10);

        assertAll(() -> assertEquals(10, t.duration()),
                () -> assertEquals(10, t.duration(tId)),
                () -> assertEquals(1, t.activeTasks()));

        clock(collector).addAndGetNanos(10);
        t.stop(tId);

        assertAll(() -> assertEquals(0, t.duration()),
                () -> assertEquals(-1, t.duration(tId)),
                () -> assertEquals(0, t.activeTasks()));
    }
}
