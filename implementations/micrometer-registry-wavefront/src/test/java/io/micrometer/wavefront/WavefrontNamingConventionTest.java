package io.micrometer.wavefront;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WavefrontNamingConventionTest {
    private final WavefrontNamingConvention convention = new WavefrontNamingConvention(null);

    @Test
    void name() {
        assertThat(convention.name("123abc/{:id}水", Meter.Type.Gauge)).isEqualTo("123abc/__id__");
    }

    @Test
    void tagKey() {
        assertThat(convention.tagKey("123abc/{:id}水")).isEqualTo("123abc___id__");
    }

    @Test
    void tagValue() {
        assertThat(convention.tagValue("123abc/\"{:id}水\\")).isEqualTo("123abc/\\\"{:id}水_");
        assertThat(convention.tagValue("\\")).isEqualTo("_");
    }
}