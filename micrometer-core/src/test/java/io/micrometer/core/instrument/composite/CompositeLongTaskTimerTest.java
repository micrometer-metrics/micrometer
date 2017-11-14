package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeLongTaskTimerTest {
    @Test
    void mapIdsToEachLongTaskTimerInComposite() {
        MockClock clock = new MockClock();
        MeterRegistry s1 = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        LongTaskTimer anotherTimer = s1.more().longTaskTimer("long.task");

        long anotherId = anotherTimer.start();
        clock.add(10, TimeUnit.NANOSECONDS);

        CompositeMeterRegistry registry = new CompositeMeterRegistry(clock);
        registry.add(s1);

        LongTaskTimer longTaskTimer = registry.more().longTaskTimer("long.task");
        long id = longTaskTimer.start();

        clock.add(100, TimeUnit.NANOSECONDS);
        assertThat(anotherTimer.stop(anotherId)).isEqualTo(110);

        // if this fails, the composite is using a timer ID that overlaps with a separate timer in a member
        // of the composite rather than mapping the ID to a separate ID in the composite member.
        assertThat(longTaskTimer.stop(id)).isEqualTo(100);
    }
}