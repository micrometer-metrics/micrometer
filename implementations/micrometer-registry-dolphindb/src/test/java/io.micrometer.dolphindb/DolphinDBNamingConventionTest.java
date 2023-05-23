package io.micrometer.dolphindb;

import io.micrometer.core.instrument.Meter;
import org.junit.Test;
//import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DolphinDBNamingConventionTest {

    private DolphinDBNamingConvention convention = new DolphinDBNamingConvention();

    @Test
    public void defaultToSnakeCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gauge_size");
    }

}
