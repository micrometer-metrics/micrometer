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

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
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
     * should include a {@code service.name} attribute that identifies your service. By
     * default, it will load resource attributes from the {@code OTEL_RESOURCE_ATTRIBUTES}
     * environment variable and the service name from the {@code OTEL_SERVICE_NAME}
     * environment variable if they are set.
     * @return map of key value pairs to use as resource attributes
     * @see <a href=
     * "https://opentelemetry.io/docs/reference/specification/resource/semantic_conventions/#service">OpenTelemetry
     * Resource Semantic Conventions</a>
     */
    default Map<String, String> resourceAttributes() {
        String resourceAttributesConfig = getString(this, "resourceAttributes").orElse(null);
        Map<String, String> env = System.getenv();
        String resourceAttributesString = resourceAttributesConfig != null ? resourceAttributesConfig
                : env.get("OTEL_RESOURCE_ATTRIBUTES");
        String[] splitResourceAttributesString = resourceAttributesString == null ? new String[] {}
                : resourceAttributesString.split(",");

        Map<String, String> resourceAttributes = Arrays.stream(splitResourceAttributesString)
                .collect(Collectors.toMap(keyvalue -> keyvalue.split("=")[0], keyvalue -> keyvalue.split("=")[1]));

        if (env.containsKey("OTEL_SERVICE_NAME")) {
            resourceAttributes.put("service.name", env.get("OTEL_SERVICE_NAME"));
        }

        return resourceAttributes;
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> PushRegistryConfig.validate(c), checkRequired("url", OtlpConfig::url));
    }

}
