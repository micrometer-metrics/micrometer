package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_KEY_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_VALUE_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.METRIC_KEY_MAX_LENGTH;

class LineProtocolNamingConventionTest implements WithAssertions {

    NamingConvention namingConvention = new LineProtocolNamingConvention();

    @Test
    void shouldMetricKeyBeTrimmed_whenIsGreaterThanMaxLength() {
        String name = stringOfSize(METRIC_KEY_MAX_LENGTH + 1);

        String metricKey = namingConvention.name(name, Meter.Type.GAUGE);

        assertThat(metricKey).hasSize(METRIC_KEY_MAX_LENGTH);
    }

    @Test
    void shouldDimensionNameBeTrimmed_whenIsGreaterThanMaxLength() {
        String key = stringOfSize(DIMENSION_KEY_MAX_LENGTH + 1);

        String dimensionName = namingConvention.tagKey(key);

        assertThat(dimensionName).hasSize(DIMENSION_KEY_MAX_LENGTH);
    }

    @Test
    void shouldDimensionValueBeTrimmed_whenIsGreaterThanMaxLength() {
        String value = stringOfSize(DIMENSION_VALUE_MAX_LENGTH + 1);

        String dimensionValue = namingConvention.tagValue(value);

        assertThat(dimensionValue).hasSize(DIMENSION_VALUE_MAX_LENGTH);
    }

    private String stringOfSize(int size) {
        return IntStream.range(0, size)
                .mapToObj(doesnotmatter -> "a")
                .collect(Collectors.joining());
    }
}
