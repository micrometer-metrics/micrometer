package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StepMaxTest {
    private final Duration step = Duration.ofMillis(10);
    MockClock clock;
    @Test
    void testMax() {
        clock = new MockClock();
        StepMax max = new StepMax(clock, step.toMillis());

        assertThat(max.poll()).isZero();
        max.record(10.0);
        assertThat(max.poll()).isZero();
        clock.add(step);
        assertThat(max.poll()).isEqualTo(10);
        max.record(1.0);
        max.record(11);
        clock.add(step.minusMillis(1));
        assertThat(max.poll()).isEqualTo(10);
        clock.add(Duration.ofMillis(1));
        assertThat(max.poll()).isEqualTo(11);
        clock.add(step);
        assertThat(max.poll()).isZero();
    }

    @Test
    void shouldPreserveCurrentAfterManualRollover() {
        clock = new MockClock();
        StepMax max = new StepMax(clock, step.toMillis());

        max.record(11);
        clock.add(step);
        assertThat(max.poll()).isEqualTo(11);
        max._closingRollover();
        clock.add(step);
        assertThat(max.poll()).isEqualTo(11);
        max.record(100);
        clock.add(step);
        assertThat(max.poll()).isEqualTo(11);
    }
}
