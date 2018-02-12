/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.signalfx;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;

public interface SignalFxConfig extends StepRegistryConfig {
    SignalFxConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "signalfx";
    }

    default String accessToken() {
        String v = get(prefix() + ".accessToken");
        if(v == null)
            throw new MissingRequiredConfigurationException("accessToken must be set to report metrics to SignalFX");
        return v;
    }

    /**
     * The URI to ship metrics to. If you need to publish metrics to an internal proxy en route to
     * SignalFx, you can define the location of the proxy with this.
     */
    default String uri() {
        String v = get(prefix() + ".apiHost");
        return v == null ? "https://ingest.signalfx.com" : v;
    }

    @Override
    default Duration step() {
        return Duration.ofSeconds(10);
    }
}
