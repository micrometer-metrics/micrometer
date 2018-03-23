package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class DynatraceNamingConventionTest {

    private final DynatraceNamingConvention convention = new DynatraceNamingConvention();

    @Test
    void nameStartsWithCustomAndColon() {
        assertThat(convention.name("mymetric", Meter.Type.COUNTER, null)).isEqualTo("custom:mymetric");
    }

}