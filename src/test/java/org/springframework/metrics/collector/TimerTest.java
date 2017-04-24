package org.springframework.metrics.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerTest {

    @DisplayName("total time and count are preserved for a single timing")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testRecord(MetricCollector collector) {
        Timer t = collector.timer("myTimer");
        t.record(42, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(t.count(), 1L),
                () -> assertEquals(t.totalTime(), 42000000L));
    }

    @DisplayName("negative times are discarded by the Timer")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testRecordNegative(MetricCollector collector) {
        Timer t = collector.timer("myTimer");
        t.record(-42, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(t.count(), 0L),
                () -> assertEquals(t.totalTime(), 0L));
    }

    @DisplayName("zero times contribute to the count of overall events but do not add to total time")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testRecordZero(MetricCollector collector) {
        Timer t = collector.timer("myTimer");
        t.record(0, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(t.count(), 1L),
                () -> assertEquals(t.totalTime(), 0L));
    }
}
