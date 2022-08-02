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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

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
    @Nullable
    default String applicationKey() {
        return getString(this, "applicationKey").orElse(null);
    }

    /**
     * @return The tag that will be mapped to "host" when shipping metrics to datadog.
     */
    @Nullable
    default String hostTag() {
        return getString(this, "hostTag").orElse("instance");
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal
     * proxy en route to datadoghq, you can define the location of the proxy with this.
     * Using a statsdURL will be preferred here over api endpoints and will report via the
     * agent. Use tcp/udp/unix URI schemes to indicate this.
     *
     * Via TCP: tcp://localhost:8125/ Via UDP: udp://localhost:8125/ Via Unix Socket:
     * unix:///var/run/datadog.sock Via Auto Environment Variable Discovery: discovery:///
     *
     * Not Recommended Default API method (backwards compatible for this micrometer
     * plugin) Via HTTPS: https://api.datadoghq.com
     */
    default String uri() {
        return getUriString(this, "uri").orElse("https://api.datadoghq.com");
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
        return checkAll(this, c -> StepRegistryConfig.validate(c),
                checkRequired("uri", DatadogConfig::uri).andThen(uriValidation -> {
                    if (uriValidation.isValid()) {
                        return checkAll(this, config -> {
                            // only check apiKey if using https method, dogstatsd talks to
                            // the local agent instead.
                            if (!DatadogMeterRegistry.isStatsd(config.uri())) {
                                return checkAll(this, checkRequired("uri", DatadogConfig::uri),
                                        checkRequired("apiKey", DatadogConfig::apiKey));
                            }
                            else {
                                return checkAll(this, checkRequired("uri", DatadogConfig::uri));
                            }
                        });
                    }
                    else {
                        return uriValidation;
                    }
                }));
    }

}
