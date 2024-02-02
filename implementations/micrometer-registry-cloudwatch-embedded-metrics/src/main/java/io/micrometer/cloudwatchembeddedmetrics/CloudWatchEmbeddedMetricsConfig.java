/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.cloudwatchembeddedmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;
import java.util.function.Predicate;

import software.amazon.cloudwatchlogs.emf.config.Configuration;
import software.amazon.cloudwatchlogs.emf.config.EnvironmentConfigurationProvider;
import software.amazon.cloudwatchlogs.emf.model.StorageResolution;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for CloudWatch Embedded Metric Format exporting.
 *
 * @author Kyle Sletmoe
 * @author Dawid Kublik
 * @since 1.14.0
 *
 * Any value found with {@link CloudWatchEmbeddedMetricsConfig::get} will be used,
 * otherwise we will fall back to the EMF environment configuration mechanism. Lastly, if
 * not found in the environment, the configuration option will not be set.
 *
 * <a href=
 * "https://github.com/awslabs/aws-embedded-metrics-java/tree/master#configuration">EMF
 * Configuration</a>
 */
public interface CloudWatchEmbeddedMetricsConfig extends StepRegistryConfig {

    /*
     * This is to aid in testing and probably should not be overridden in a production
     * environment
     */
    default Configuration emfConfiguration() {
        return EnvironmentConfigurationProvider.getConfig();
    }

    @Override
    default String prefix() {
        return "cloudwatchemf";
    }

    /*
     * For much of our config, we want to take any values explicitly set on this config
     * object, and if those aren't set, utilize any pulled from the environment by EMF.
     */
    @Nullable
    default String logGroupName() {
        return ConfigFetcher.getOptionalConfig(this, "logGroupName", emfConfiguration().getLogGroupName().orElse(null));
    }

    @Nullable
    default String logStreamName() {
        return ConfigFetcher.getOptionalConfig(this, "logStreamName",
                emfConfiguration().getLogStreamName().orElse(null));
    }

    @Nullable
    default String serviceType() {
        return ConfigFetcher.getOptionalConfig(this, "serviceType", emfConfiguration().getServiceType().orElse(null));
    }

    @Nullable
    default String serviceName() {
        return ConfigFetcher.getOptionalConfig(this, "serviceName", emfConfiguration().getServiceName().orElse(null));
    }

    @Nullable
    default String namespace() {
        return getString(this, "namespace").orElse(null);
    }

    @Nullable
    default String agentEndpoint() {
        return ConfigFetcher.getOptionalConfig(this, "agentEndpoint",
                emfConfiguration().getAgentEndpoint().orElse(null));
    }

    default Integer asyncBufferSize() {
        Integer configuredValue = getInteger(this, "asyncBufferSize").orElse(null);

        return configuredValue != null ? configuredValue : emfConfiguration().getAsyncBufferSize();
    }

    /**
     * Whether we want to emit default dimensions. Defaults to true.
     * @return The decision about whether to use default dimensions
     */
    default boolean useDefaultDimensions() {
        return getBoolean(this, "useDefaultDimensions").orElse(true);
    }

    /**
     * Whether to ship high-resolution metrics to CloudWatch at a higher cost. By default,
     * if the step interval is less than one minute, we assume that high-resolution
     * metrics are also desired.
     * <p>
     * This is incubating because CloudWatch supports making this decision on a per-metric
     * level. It's believed that deciding on a per-registry level leads to simpler
     * configuration and will be satisfactory in most cases. To only ship a certain subset
     * of metrics at high resolution, two {@link CloudWatchEmbeddedMetricsMeterRegistry}
     * instances can be configured. One is configured with high-resolution and a
     * {@link MeterFilter#denyUnless(Predicate)} filter. The other is configured with
     * low-resolution and a {@link MeterFilter#deny(Predicate)} filter. Both use the same
     * predicate.
     * @return The decision about whether to accept higher cost high-resolution metrics.
     * @since 1.13.0
     */
    @Incubating(since = "1.13.0")
    default StorageResolution storageResolution() {
        return step().compareTo(Duration.ofMinutes(1)) < 0 ? StorageResolution.HIGH : StorageResolution.STANDARD;
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, (CloudWatchEmbeddedMetricsConfig c) -> StepRegistryConfig.validate(c));
    }

    class ConfigFetcher {

        static String getOptionalConfig(CloudWatchEmbeddedMetricsConfig config, String property,
                @Nullable String environmentConfigValue) {
            String configuredValue = getString(config, property).orElse(null);

            return configuredValue != null ? configuredValue : environmentConfigValue;
        }

    }

}
