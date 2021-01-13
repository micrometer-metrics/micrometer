/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    void shouldMetricKeyBeSanitized_whenItContainsSpecialChars() {
        String name = "my,metric";

        String metricKey = namingConvention.name(name, Meter.Type.GAUGE);

        assertThat(metricKey).matches("my_metric");
    }

    @Test
    void shouldDimensionNameBeTrimmed_whenIsGreaterThanMaxLength() {
        String key = stringOfSize(DIMENSION_KEY_MAX_LENGTH + 1);

        String dimensionName = namingConvention.tagKey(key);

        assertThat(dimensionName).hasSize(DIMENSION_KEY_MAX_LENGTH);
    }

    @Test
    void shouldDimensionNameBeSanitized_whenItContainsSpecialChars() {
        String key = "country#lang";

        String dimensionName = namingConvention.tagKey(key);

        assertThat(dimensionName).matches("country_lang");
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
