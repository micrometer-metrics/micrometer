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
package io.micrometer.humio;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link HumioMeterRegistry}.
 *
 * @author Martin Westergaard Lassen
 * @since 1.1.0
 */
public interface HumioConfig extends StepRegistryConfig {

    HumioConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "humio";
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal
     * proxy en route to Humio, you can define the location of the proxy with this.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("https://cloud.humio.com");
    }

    /**
     * Humio uses a concept called "tags" to decide which datasource to store metrics in.
     * This concept is distinct from Micrometer's notion of tags, which divides a metric
     * along dimensional boundaries. All metrics from this registry will be stored under a
     * datasource defined by these tags.
     * @return Tags which uniquely determine the datasource to store metrics in.
     */
    @Nullable
    default Map<String, String> tags() {
        return null;
    }

    @Nullable
    default String apiToken() {
        return getSecret(this, "apiToken").orElse(null);
    }

    @Deprecated
    @Override
    default Duration connectTimeout() {
        return getDuration(this, "connectTimeout").orElse(Duration.ofSeconds(5));
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("uri", HumioConfig::uri));
    }

}
