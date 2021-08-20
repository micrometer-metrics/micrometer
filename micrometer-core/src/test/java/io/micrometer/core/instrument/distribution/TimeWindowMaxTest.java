package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TimeWindowMax}
 */
class TimeWindowMaxTest {

    MockClock clock = new MockClock();
    TimeWindowMax timeWindowMax;

    @Test
    void decaysToZero() {
        int bufferLength = 3;
        long rotateFrequencyMillis = Duration.ofMinutes(1).toMillis();
        timeWindowMax = new TimeWindowMax(clock, rotateFrequencyMillis, bufferLength);
        timeWindowMax.record(100);

        for (int i = 0; i < bufferLength; i++) {
            assertThat(timeWindowMax.poll()).isEqualTo(100);
            clock.add(rotateFrequencyMillis, TimeUnit.MILLISECONDS);
        }

        assertThat(timeWindowMax.poll()).isZero();
    }
}
