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
package io.micrometer.signalfx;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link SignalFxMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface SignalFxConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "signalfx";
    }

    default String accessToken() {
        return getSecret(this, "accessToken").required().get();
    }

    /**
     * @return {@code true} if the SignalFx registry should emit cumulative histogram buckets.
     */
    default boolean publishCumulativeHistogram() {
        return getBoolean(this, "publishCumulativeHistogram").orElse(false);
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal proxy en route to
     * SignalFx, you can define the location of the proxy with this.
     */
    default String uri() {
        // NOTE: at some point, the property 'apiHost' diverged from the name of the method 'uri', so we accept
        // either here for backwards compatibility.
        return getUrlString(this, "apiHost")
                .flatMap((uri, valid) -> uri == null ? getUrlString(this, "uri") : valid)
                .orElse("https://ingest.signalfx.com");
    }

    /**
     * @return Unique identifier for the app instance that is publishing metrics to SignalFx. Defaults to the local host name.
     */
    default String source() {
        return getString(this, "source").orElseGet(() -> {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException uhe) {
                return "unknown";
            }
        });
    }

    @Override
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofSeconds(10));
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("accessToken", SignalFxConfig::accessToken),
                checkRequired("uri", SignalFxConfig::uri),
                checkRequired("source", SignalFxConfig::source)
        );
    }
}
