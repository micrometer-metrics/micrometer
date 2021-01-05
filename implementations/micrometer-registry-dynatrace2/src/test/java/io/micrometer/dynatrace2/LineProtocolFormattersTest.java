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

import io.micrometer.core.instrument.Tag;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static io.micrometer.dynatrace2.LineProtocolFormatters.formatCounterMetricLine;
import static io.micrometer.dynatrace2.LineProtocolFormatters.formatGaugeMetricLine;
import static io.micrometer.dynatrace2.LineProtocolFormatters.formatTimerMetricLine;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

class LineProtocolFormattersTest implements WithAssertions {

    @Test
    void shouldCreateAGaugeMetricLine_whenDimensionsAreEmpty() {
        String metricLine = formatGaugeMetricLine("my.metric", emptyList(), 3.33, 12345);

        assertThat(metricLine).isEqualTo("my.metric 3.33 12345");
    }

    @Test
    void shouldCreateAGaugeMetricLine_whenMultipleDimensions() {
        String metricLine = formatGaugeMetricLine(
                "my.metric",
                asList(Tag.of("country", "es"), Tag.of("city", "bcn")),
                3.33,
                12345);

        assertThat(metricLine).isEqualTo("my.metric,country=\"es\",city=\"bcn\" 3.33 12345");
    }

    @Test
    void shouldCreateACounterMetricLine_whenDimensionsAreEmpty() {
        String metricLine = formatCounterMetricLine("my.metric", emptyList(), 5, 12345);

        assertThat(metricLine).isEqualTo("my.metric count,delta=5 12345");
    }

    @Test
    void shouldCreateACounterMetricLine_whenMultipleDimensions() {
        String metricLine = formatCounterMetricLine(
                "my.metric",
                asList(Tag.of("country", "es"), Tag.of("city", "bcn")),
                5,
                12345);

        assertThat(metricLine).isEqualTo("my.metric,country=\"es\",city=\"bcn\" count,delta=5 12345");
    }

    @Test
    void shouldCreateATimerMetricLine_whenDimensionsAreEmpty() {
        String metricLine = formatTimerMetricLine("my.metric", emptyList(), 5, 12345);

        assertThat(metricLine).isEqualTo("my.metric gauge,5 12345");
    }

    @Test
    void shouldCreateATimerMetricLine_whenMultipleDimensions() {
        String metricLine = formatTimerMetricLine(
                "my.metric",
                asList(Tag.of("country", "es"), Tag.of("city", "bcn")),
                5,
                12345);

        assertThat(metricLine).isEqualTo("my.metric,country=\"es\",city=\"bcn\" gauge,5 12345");
    }

    @Test
    void shouldSucceed_whenTagKeyContainsSpecialChar() {
        String metricLine = formatTimerMetricLine(
                "my.metric",
                asList(Tag.of("country#lang", "es"), Tag.of("city", "bcn")),
                5,
                12345);

        assertThat(metricLine).isEqualTo("my.metric,country_lang=\"es\",city=\"bcn\" gauge,5 12345");
    }
}
