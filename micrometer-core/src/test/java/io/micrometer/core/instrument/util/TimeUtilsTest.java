package io.micrometer.core.instrument.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {
    @Test
    void simpleParse() {
        assertThat(TimeUtils.simpleParse("5ns")).isEqualByComparingTo(Duration.ofNanos(5));
        assertThat(TimeUtils.simpleParse("700ms")).isEqualByComparingTo(Duration.ofMillis(700));
        assertThat(TimeUtils.simpleParse("1s")).isEqualByComparingTo(Duration.ofSeconds(1));
        assertThat(TimeUtils.simpleParse("10m")).isEqualByComparingTo(Duration.ofMinutes(10));
        assertThat(TimeUtils.simpleParse("13h")).isEqualByComparingTo(Duration.ofHours(13));
        assertThat(TimeUtils.simpleParse("5d")).isEqualByComparingTo(Duration.ofDays(5));
    }

    @Test
    void cantParseDecimal(){
        assertThatThrownBy(() -> TimeUtils.simpleParse("1.1s"))
            .isInstanceOf(NumberFormatException.class);
    }

}
