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
package io.micrometer.datadog;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import org.jspecify.annotations.Nullable;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link DatadogMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface DatadogConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "datadog";
    }

    default String apiKey() {
        return getString(this, "apiKey").required().get();
    }

    /**
     * @return The Datadog application key. This is only required if you care for metadata
     * like base units, description, and meter type to be published to Datadog.
     */
    default @Nullable String applicationKey() {
        return getString(this, "applicationKey").orElse(null);
    }

    /**
     * @return The tag that will be mapped to "host" when shipping metrics to datadog.
     */
    default @Nullable String hostTag() {
        return getString(this, "hostTag").orElse("instance");
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal
     * proxy en route to datadoghq, you can define the location of the proxy with this.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("https://api.datadoghq.com");
    }

    /**
     * @return {@code true} if meter descriptions should be sent to Datadog. Turn this off
     * to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        return getBoolean(this, "descriptions").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("apiKey", DatadogConfig::apiKey),
                checkRequired("uri", DatadogConfig::uri));
    }

}
