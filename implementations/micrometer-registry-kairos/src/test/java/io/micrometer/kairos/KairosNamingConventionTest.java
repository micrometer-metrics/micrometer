package io.micrometer.kairos;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Anton Ilinchik
 */
class KairosNamingConventionTest {

    private KairosNamingConvention convention = new KairosNamingConvention();

    @Test
    void dotNotationIsConvertedToSnakeCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gauge_size");
    }

}