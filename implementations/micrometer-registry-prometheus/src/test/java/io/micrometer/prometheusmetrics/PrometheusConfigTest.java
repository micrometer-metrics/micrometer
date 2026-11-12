/*
 * Copyright 2020 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheusmetrics;

import io.micrometer.core.instrument.config.validate.Validated;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PrometheusConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final PrometheusConfig config = props::get;

    @Test
    void invalid() {
        props.put("prometheus.step", "1w");

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactly("must contain a valid time unit");
    }

    @Test
    void valid() {
        assertThat(config.validate().isValid()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\n\t" })
    void prometheusPropertiesDefaults(String value) {
        props.put("prometheus.prometheusProperties", value);

        assertThat(config.prometheusProperties())
            .containsEntry("io.prometheus.exporter.exemplarsOnAllMetricTypes", "true")
            .hasSize(1);
    }

    @Test
    void prometheusPropertiesFromGetPreserveDefaults() {
        props.put("prometheus.prometheusProperties", "io.prometheus.exemplars.sampleIntervalMilliseconds=42");

        assertThat(config.prometheusProperties())
            .containsEntry("io.prometheus.exporter.exemplarsOnAllMetricTypes", "true")
            .containsEntry("io.prometheus.exemplars.sampleIntervalMilliseconds", "42")
            .hasSize(2);
    }

    @Test
    void prometheusPropertiesFromGetOverrideDefaults() {
        props.put("prometheus.prometheusProperties", "io.prometheus.exporter.exemplarsOnAllMetricTypes=false");

        assertThat(config.prometheusProperties())
            .containsEntry("io.prometheus.exporter.exemplarsOnAllMetricTypes", "false")
            .hasSize(1);
    }

    @Test
    void prometheusPropertiesUsePropertiesSyntax() {
        props.put("prometheus.prometheusProperties",
                "# Client configuration\n" + " io.prometheus.exemplars.sampleIntervalMilliseconds = 42\r\n"
                        + "io.prometheus.metrics.histogramClassicUpperBounds=0.1, 0.5, 1.0\n"
                        + "custom=value=with=equals\n" + "io.prometheus.exemplars.sampleIntervalMilliseconds=43");

        assertThat(config.prometheusProperties())
            .containsEntry("io.prometheus.exporter.exemplarsOnAllMetricTypes", "true")
            .containsEntry("io.prometheus.exemplars.sampleIntervalMilliseconds", "43")
            .containsEntry("io.prometheus.metrics.histogramClassicUpperBounds", "0.1, 0.5, 1.0")
            .containsEntry("custom", "value=with=equals")
            .hasSize(4);
    }

    @Test
    void configuredPropertiesAreAcceptedByPrometheusClient() {
        props.put("prometheus.prometheusProperties", "io.prometheus.exporter.exemplarsOnAllMetricTypes=false\n"
                + "io.prometheus.metrics.histogramClassicUpperBounds=0.1, 0.5, 1.0");

        PrometheusProperties properties = PrometheusPropertiesLoader.load(config.prometheusProperties());

        assertThat(properties.getExporterProperties().getExemplarsOnAllMetricTypes()).isFalse();
        assertThat(properties.getDefaultMetricProperties().getHistogramClassicUpperBounds()).containsExactly(0.1, 0.5,
                1.0);
    }

    @Test
    void malformedPrometheusPropertiesAreRejected() {
        props.put("prometheus.prometheusProperties", "custom=\\uXXXX");

        assertThatIllegalArgumentException().isThrownBy(config::prometheusProperties);
    }

}
