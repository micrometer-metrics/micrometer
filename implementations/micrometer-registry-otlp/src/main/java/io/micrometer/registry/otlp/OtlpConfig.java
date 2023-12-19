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

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Config for {@link OtlpMeterRegistry}.
 *
 * @author Tommy Ludwig
 * @author Lenin Jaganathan
 * @since 1.9.0
 */
public interface OtlpConfig extends PushRegistryConfig {

    /**
     * Configuration with default values.
     */
    OtlpConfig DEFAULT = key -> null;

    @Override
    default String prefix() {
        return "otlp";
    }

    /**
     * Defaults to http://localhost:4318/v1/metrics
     * @return address to where metrics will be published.
     */
    default String url() {
        return getUrlString(this, "url").orElseGet(() -> {
            Map<String, String> env = System.getenv();
            String endpoint = env.get("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT");
            if (endpoint == null) {
                endpoint = env.get("OTEL_EXPORTER_OTLP_ENDPOINT");
            }
            if (endpoint == null) {
                endpoint = "http://localhost:4318/v1/metrics";
            }
            else if (!endpoint.endsWith("/v1/metrics")) {
                endpoint = endpoint + "/v1/metrics";
            }
            return endpoint;
        });
    }

    @Override
    default Duration step() {
        Validated<Duration> step = getDuration(this, "step");
        return step.orElseGet(() -> {
            String exportInterval = System.getenv().get("OTEL_METRIC_EXPORT_INTERVAL");
            if (exportInterval != null) {
                return Duration.ofMillis(Long.parseLong(exportInterval));
            }
            return PushRegistryConfig.super.step();
        });
    }

    /**
     * Attributes to set on the Resource that will be used for all metrics published. This
     * should include a {@code service.name} attribute that identifies your service.
     * <p>
     * By default, resource attributes will load using the {@link #get(String)} method,
     * extracting key values from a comma-separated list in the format
     * {@code key1=value1,key2=value2}. Resource attributes will be loaded from the
     * {@code OTEL_RESOURCE_ATTRIBUTES} environment variable and the service name from the
     * {@code OTEL_SERVICE_NAME} environment variable if they are set and
     * {@link #get(String)} does not return a value.
     * @return map of key value pairs to use as resource attributes
     * @see <a href=
     * "https://opentelemetry.io/docs/reference/specification/resource/semantic_conventions/#service">OpenTelemetry
     * Resource Semantic Conventions</a>
     */
    default Map<String, String> resourceAttributes() {
        Map<String, String> env = System.getenv();
        String resourceAttributesConfig = getString(this, "resourceAttributes")
            .orElse(env.get("OTEL_RESOURCE_ATTRIBUTES"));
        String[] splitResourceAttributesString = resourceAttributesConfig == null ? new String[] {}
                : resourceAttributesConfig.trim().split(",");

        Map<String, String> resourceAttributes = Arrays.stream(splitResourceAttributesString)
            .map(String::trim)
            .filter(keyValue -> keyValue.length() > 2 && keyValue.indexOf('=') > 0)
            .collect(Collectors.toMap(keyvalue -> keyvalue.substring(0, keyvalue.indexOf('=')).trim(),
                    keyValue -> keyValue.substring(keyValue.indexOf('=') + 1).trim()));

        if (env.containsKey("OTEL_SERVICE_NAME") && !resourceAttributes.containsKey("service.name")) {
            resourceAttributes.put("service.name", env.get("OTEL_SERVICE_NAME"));
        }

        return resourceAttributes;
    }

    /**
     * {@link AggregationTemporality} of the OtlpMeterRegistry. This determines whether
     * the meters should be cumulative(AGGREGATION_TEMPORALITY_CUMULATIVE) or
     * step/delta(AGGREGATION_TEMPORALITY_DELTA).
     * @return the aggregationTemporality for OtlpMeterRegistry
     * @see <a href=
     * "https://opentelemetry.io/docs/reference/specification/metrics/data-model/#temporality">OTLP
     * Temporality</a>
     * @since 1.11.0
     */
    default AggregationTemporality aggregationTemporality() {
        return getEnum(this, AggregationTemporality.class, "aggregationTemporality").orElseGet(() -> {
            String preference = System.getenv().get("OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE");
            if (preference != null) {
                return AggregationTemporality.valueOf(preference.toUpperCase());
            }
            return AggregationTemporality.CUMULATIVE;
        });
    }

    /**
     * Additional headers to send with exported metrics. This may be needed for
     * authorization headers, for example.
     * <p>
     * By default, headers will be loaded from {@link #get(String)}. If that is not set,
     * they will be taken from the environment variables
     * {@code OTEL_EXPORTER_OTLP_HEADERS} and {@code OTEL_EXPORTER_OTLP_METRICS_HEADERS}.
     * The header key-value pairs are expected to be in a comma-separated list in the
     * format {@code key1=value1,key2=value2}. If a header is set in both
     * {@code OTEL_EXPORTER_OTLP_HEADERS} and {@code OTEL_EXPORTER_OTLP_METRICS_HEADERS},
     * the header in the latter will overwrite the former.
     * @return a map of the headers' key-value pairs
     * @see <a href=
     * "https://opentelemetry.io/docs/reference/specification/protocol/exporter/#specifying-headers-via-environment-variables">OTLP
     * Exporer headers configuration</a>
     * @since 1.11.0
     */
    default Map<String, String> headers() {
        String headersString = getString(this, "headers").orElse(null);

        if (headersString == null) {
            Map<String, String> env = System.getenv();
            // common headers
            headersString = env.getOrDefault("OTEL_EXPORTER_OTLP_HEADERS", "").trim();
            String metricsHeaders = env.getOrDefault("OTEL_EXPORTER_OTLP_METRICS_HEADERS", "").trim();
            headersString = Objects.equals(headersString, "") ? metricsHeaders : headersString + "," + metricsHeaders;
        }

        String[] keyValues = Objects.equals(headersString, "") ? new String[] {} : headersString.split(",");

        return Arrays.stream(keyValues)
            .map(String::trim)
            .filter(keyValue -> keyValue.length() > 2 && keyValue.indexOf('=') > 0)
            .collect(Collectors.toMap(keyValue -> keyValue.substring(0, keyValue.indexOf('=')).trim(),
                    keyValue -> keyValue.substring(keyValue.indexOf('=') + 1).trim(), (l, r) -> r));
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> PushRegistryConfig.validate(c), checkRequired("url", OtlpConfig::url),
                check("resourceAttributes", OtlpConfig::resourceAttributes),
                check("baseTimeUnit", OtlpConfig::baseTimeUnit),
                check("aggregationTemporality", OtlpConfig::aggregationTemporality));
    }

    default TimeUnit baseTimeUnit() {
        return getTimeUnit(this, "baseTimeUnit").orElse(TimeUnit.MILLISECONDS);
    }

}
