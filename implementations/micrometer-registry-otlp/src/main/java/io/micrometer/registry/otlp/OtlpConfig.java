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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Config for {@link OtlpMeterRegistry}.
 *
 * @author Tommy Ludwig
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
        return getUrlString(this, "url").orElse("http://localhost:4318/v1/metrics");
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

        Map<String, String> resourceAttributes = Arrays.stream(splitResourceAttributesString).map(String::trim)
                .filter(keyvalue -> keyvalue.length() > 2 && keyvalue.indexOf('=') > 0)
                .collect(Collectors.toMap(keyvalue -> keyvalue.substring(0, keyvalue.indexOf('=')).trim(),
                        keyvalue -> keyvalue.substring(keyvalue.indexOf('=') + 1).trim()));

        if (env.containsKey("OTEL_SERVICE_NAME") && !resourceAttributes.containsKey("service.name")) {
            resourceAttributes.put("service.name", env.get("OTEL_SERVICE_NAME"));
        }

        return resourceAttributes;
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
     */
    default Map<String, String> headers() {
        Map<String, String> env = System.getenv();
        String headersString = getString(this, "headers").orElse(null);

        if (headersString == null) {
            headersString = env.getOrDefault("OTEL_EXPORTER_OTLP_HEADERS", "").trim(); // common
                                                                                       // headers
            String metricsHeaders = env.getOrDefault("OTEL_EXPORTER_OTLP_METRICS_HEADERS", "").trim();
            headersString = "".equals(headersString) ? metricsHeaders : headersString + "," + metricsHeaders;
        }

        String[] keyvalues = "".equals(headersString) ? new String[] {} : headersString.split(",");

        return Arrays.stream(keyvalues).map(String::trim)
                .filter(keyvalue -> keyvalue.length() > 2 && keyvalue.indexOf('=') > 0)
                .collect(Collectors.toMap(keyvalue -> keyvalue.substring(0, keyvalue.indexOf('=')).trim(),
                        keyvalue -> keyvalue.substring(keyvalue.indexOf('=') + 1).trim(), (l, r) -> r));
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> PushRegistryConfig.validate(c), checkRequired("url", OtlpConfig::url),
                check("resourceAttributes", OtlpConfig::resourceAttributes));
    }

}
