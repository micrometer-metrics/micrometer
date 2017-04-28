package org.springframework.metrics.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributionSummaryTest {

    @DisplayName("multiple recordings are maintained")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void record(MeterRegistry collector) {
        DistributionSummary ds = collector.distributionSummary("myDistributionSummary");

        ds.record(10);
        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(10L, ds.totalAmount()));


        ds.record(10);
        assertAll(() -> assertEquals(2L, ds.count()),
                () -> assertEquals(20L, ds.totalAmount()));
    }

    @DisplayName("negative quantities are ignored")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordNegative(MeterRegistry collector) {
        DistributionSummary ds = collector.distributionSummary("myDistributionSummary");

        ds.record(-10);
        assertAll(() -> assertEquals(0, ds.count()),
                () -> assertEquals(-0L, ds.totalAmount()));
    }

    @DisplayName("record zero")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordZero(MeterRegistry collector) {
        DistributionSummary ds = collector.distributionSummary("myDistributionSummary");

        ds.record(0);
        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(0L, ds.totalAmount()));
    }
}
