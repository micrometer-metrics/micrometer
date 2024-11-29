/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.config.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

/**
 * Tests for {@link OtlpConfig}.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
class OtlpConfigTest {

    @Test
    void resourceAttributesInputParsing() {
        OtlpConfig config = k -> "key1=value1,";
        assertThat(config.resourceAttributes()).containsEntry("key1", "value1").hasSize(1);
        config = k -> "k=v,a";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").hasSize(1);
        config = k -> "k=v,a==";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").containsEntry("a", "=").hasSize(2);
        config = k -> " k = v, a= b ";
        assertThat(config.resourceAttributes()).containsEntry("k", "v").containsEntry("a", "b").hasSize(2);
    }

    @Test
    void headersEmptyishInputParsing() {
        Stream<OtlpConfig> configs = Stream.of(k -> null, k -> "", k -> "  ", k -> " ,");
        configs.forEach(config -> assertThat(config.headers()).isEmpty());
    }

    @Test
    void urlConfigTakesPrecedenceOverEnvVars() throws Exception {
        OtlpConfig config = k -> "http://url1";
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT", "http://url2")
            .execute(() -> assertThat(config.url()).isEqualTo("http://url1"));
    }

    @Test
    void urlUseEnvVarWhenConfigNotSet() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT", "http://url2")
            .execute(() -> assertThat(config.url()).isEqualTo("http://url2/v1/metrics"));
    }

    @Test
    void metricUrlHasPrecedence() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariables("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT", "http://url3", "OTEL_EXPORTER_OTLP_ENDPOINT",
                "http://url2")
            .execute(() -> assertThat(config.url()).isEqualTo("http://url3/v1/metrics"));
    }

    @Test
    void stepConfigTakesPrecedenceOverEnvVars() throws Exception {
        OtlpConfig config = k -> "10s";
        withEnvironmentVariable("OTEL_METRIC_EXPORT_INTERVAL", "20000")
            .execute(() -> assertThat(config.step()).isEqualTo(Duration.ofMillis(10000)));
    }

    @Test
    void stepUseEnvVarWhenConfigNotSet() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_METRIC_EXPORT_INTERVAL", "20000")
            .execute(() -> assertThat(config.step()).isEqualTo(Duration.ofMillis(20000)));
    }

    @Test
    void headersConfigTakesPrecedenceOverEnvVars() throws Exception {
        OtlpConfig config = k -> "header1=value1";
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS", "header2=value")
            .execute(() -> assertThat(config.headers()).containsEntry("header1", "value1").hasSize(1));
    }

    @Test
    void headersUseEnvVarWhenConfigNotSet() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS", "header2=va%20lue,header3=f oo")
            .execute(() -> assertThat(config.headers()).containsEntry("header2", "va lue")
                .containsEntry("header3", "f oo")
                .hasSize(2));
    }

    @Test
    void headersDecodingError() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS", "header2=%-1").execute(() -> {
            assertThatThrownBy(config::headers).isInstanceOf(InvalidConfigurationException.class)
                .hasMessage("Cannot URL decode headers value: header2=%-1,");
        });
    }

    @Test
    void combineHeadersFromEnvVars() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariables().set("OTEL_EXPORTER_OTLP_HEADERS", "common=v")
            .set("OTEL_EXPORTER_OTLP_METRICS_HEADERS", "metrics=m")
            .execute(() -> assertThat(config.headers()).containsEntry("common", "v")
                .containsEntry("metrics", "m")
                .hasSize(2));
    }

    @Test
    void metricsHeadersEnvVarOverwritesGenericHeadersEnvVar() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariables().set("OTEL_EXPORTER_OTLP_HEADERS", "metrics=m,auth=token")
            .set("OTEL_EXPORTER_OTLP_METRICS_HEADERS", "metrics=t")
            .execute(() -> assertThat(config.headers()).containsEntry("auth", "token")
                .containsEntry("metrics", "t")
                .hasSize(2));
    }

    @Test
    void resourceAttributesFromEnvironmentVariables() throws Exception {
        withEnvironmentVariables("OTEL_RESOURCE_ATTRIBUTES", "a=1,b=2", "OTEL_SERVICE_NAME", "my-service")
            .execute(() -> {
                assertThat(OtlpConfig.DEFAULT.resourceAttributes()).hasSize(3)
                    .containsEntry("a", "1")
                    .containsEntry("b", "2")
                    .containsEntry("service.name", "my-service");
            });
    }

    @Test
    void resourceAttributesFromGetTakePrecedenceOverOnesFromEnvironmentVariables() throws Exception {
        Map<String, String> map = Collections.singletonMap("otlp.resourceAttributes",
                "a=100,service.name=your-service");
        OtlpConfig config = map::get;
        withEnvironmentVariables("OTEL_RESOURCE_ATTRIBUTES", "a=1,b=2", "OTEL_SERVICE_NAME", "my-service")
            .execute(() -> {
                assertThat(config.resourceAttributes()).hasSize(2)
                    .containsEntry("a", "100")
                    .containsEntry("service.name", "your-service");
            });
    }

    @Test
    void aggregationTemporalityDefault() {
        Map<String, String> properties = new HashMap<>();

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.aggregationTemporality()).isSameAs(AggregationTemporality.CUMULATIVE);

        properties.put("otlp.aggregationTemporality", AggregationTemporality.CUMULATIVE.name());
        assertThat(otlpConfig.aggregationTemporality()).isSameAs(AggregationTemporality.CUMULATIVE);
    }

    @Test
    void aggregationTemporalityDelta() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.aggregationTemporality", AggregationTemporality.DELTA.name());

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.aggregationTemporality()).isSameAs(AggregationTemporality.DELTA);
    }

    @Test
    void aggregationTemporalityConfigTakesPrecedenceOverEnvVars() throws Exception {
        OtlpConfig config = k -> "DELTA";
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE", "CUMULATIVE")
            .execute(() -> assertThat(config.aggregationTemporality()).isEqualTo(AggregationTemporality.DELTA));
    }

    @Test
    void aggregationTemporalityUseEnvVarWhenConfigNotSet() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE", "DELTA")
            .execute(() -> assertThat(config.aggregationTemporality()).isEqualTo(AggregationTemporality.DELTA));
    }

    @Test
    void invalidAggregationTemporalityShouldBeCaptured() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.aggregationTemporality", "some_random_thing");

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isFalse();
    }

    @Test
    void baseTimeUnitDefault() {
        Map<String, String> properties = new HashMap<>();

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.baseTimeUnit()).isSameAs(TimeUnit.MILLISECONDS);

        properties.put("otlp.baseTimeUnit", TimeUnit.MILLISECONDS.name());
        assertThat(otlpConfig.baseTimeUnit()).isSameAs(TimeUnit.MILLISECONDS);
    }

    @Test
    void baseTimeUnitSeconds() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.baseTimeUnit", TimeUnit.SECONDS.name());

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.baseTimeUnit()).isSameAs(TimeUnit.SECONDS);
    }

    @Test
    void invalidBaseTimeUnitShouldBeCaptured() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.baseTimeUnit", "some_random_thing");

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isFalse();
    }

    @Test
    void maxScaleAndMaxBucketsDefault() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.maxScale", "8");
        properties.put("otlp.maxBucketCount", "80");

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.maxScale()).isSameAs(8);
        assertThat(otlpConfig.maxBucketCount()).isSameAs(80);
    }

    @Test
    void histogramPreference() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otlp.histogramFlavor", "base2_exponential_bucket_histogram");

        OtlpConfig otlpConfig = properties::get;
        assertThat(otlpConfig.validate().isValid()).isTrue();
        assertThat(otlpConfig.histogramFlavor()).isEqualTo(HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
    }

    @Test
    void histogramPreferenceConfigTakesPrecedenceOverEnvVars() throws Exception {
        OtlpConfig config = k -> "base2_exponential_bucket_histogram";
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION", "explicit_bucket_histogram")
            .execute(() -> assertThat(config.histogramFlavor())
                .isEqualTo(HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM));
    }

    @Test
    void histogramPreferenceUseEnvVarWhenConfigNotSet() throws Exception {
        OtlpConfig config = k -> null;
        withEnvironmentVariable("OTEL_EXPORTER_OTLP_METRICS_DEFAULT_HISTOGRAM_AGGREGATION",
                "base2_exponential_bucket_histogram")
            .execute(() -> assertThat(config.histogramFlavor())
                .isEqualTo(HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM));
    }

}
