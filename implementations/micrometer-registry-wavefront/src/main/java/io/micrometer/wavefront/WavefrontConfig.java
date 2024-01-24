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
package io.micrometer.wavefront;

import com.wavefront.sdk.common.clients.service.token.TokenService;
import com.wavefront.sdk.common.clients.service.token.TokenService.Type;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link WavefrontMeterRegistry}.
 *
 * @author Howard Yoo
 * @author Jon Schneider
 * @since 1.0.0
 */
public interface WavefrontConfig extends PushRegistryConfig {

    /**
     * Publishes to a wavefront sidecar running out of process.
     */
    WavefrontConfig DEFAULT_PROXY = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String uri() {
            String v = get(prefix() + ".uri");
            return v == null ? "proxy://localhost:2878" : v;
        }
    };

    /**
     * Publishes directly to the Wavefront API, not passing through a sidecar.
     */
    WavefrontConfig DEFAULT_DIRECT = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String uri() {
            String v = get(prefix() + ".uri");
            return v == null ? "https://longboard.wavefront.com" : v;
        }
    };

    @Override
    default String prefix() {
        return "wavefront";
    }

    /**
     * @return The URI to publish metrics to. The URI could represent a Wavefront sidecar
     * or the Wavefront API host. This host could also represent an internal proxy set up
     * in your environment that forwards metrics data to the Wavefront API host.
     * <p>
     * If publishing metrics to a Wavefront proxy (as described in
     * https://docs.wavefront.com/proxies_installing.html), the host must be in the
     * proxy://HOST:PORT format.
     */
    default String uri() {
        return getUriString(this, "uri").required().get();
    }

    /**
     * Get distribution port.
     * @return distribution port
     * @deprecated since 1.5.0 this is no longer used as a single proxy port can handle
     * all wavefront formats.
     */
    @Deprecated
    default int distributionPort() {
        return -1;
    }

    /**
     * @return Unique identifier for the app instance that is publishing metrics to
     * Wavefront. Defaults to the local host name.
     */
    default String source() {
        return getString(this, "source").orElseGet(() -> {
            try {
                return InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException uhe) {
                return "unknown";
            }
        });
    }

    /**
     * API token type.
     * @return API token type
     * @since 1.12.0
     */
    default TokenService.Type apiTokenType() {
        return getEnum(this, TokenService.Type.class, "apiTokenType")
            .invalidateWhen(tokenType -> tokenType == Type.NO_TOKEN && WavefrontMeterRegistry.isDirectToApi(this),
                    "must be set to something else whenever publishing directly to the Wavefront API",
                    InvalidReason.MISSING)
            .orElse(WavefrontMeterRegistry.isDirectToApi(this) ? Type.WAVEFRONT_API_TOKEN : Type.NO_TOKEN);
    }

    /**
     * Required when publishing directly to the Wavefront API host, otherwise does
     * nothing.
     * @return The Wavefront API token.
     */
    @Nullable
    default String apiToken() {
        return getSecret(this, "apiToken")
            .invalidateWhen(token -> token == null && WavefrontMeterRegistry.isDirectToApi(this),
                    "must be set whenever publishing directly to the Wavefront API", InvalidReason.MISSING)
            .orElse(null);
    }

    /**
     * @return {@code true} to report histogram distributions aggregated into minute
     * intervals. Default is {@code true}.
     * @since 1.2.0
     */
    default boolean reportMinuteDistribution() {
        return getBoolean(this, "reportMinuteDistribution").orElse(true);
    }

    /**
     * @return {@code true} to report histogram distributions aggregated into hour
     * intervals. Default is {@code false}.
     * @since 1.2.0
     */
    default boolean reportHourDistribution() {
        return getBoolean(this, "reportHourDistribution").orElse(false);
    }

    /**
     * @return {@code true} to report histogram distributions aggregated into day
     * intervals. Default is {@code false}.
     * @since 1.2.0
     */
    default boolean reportDayDistribution() {
        return getBoolean(this, "reportDayDistribution").orElse(false);
    }

    /**
     * Wavefront metrics are grouped hierarchically by name in the UI. Setting a global
     * prefix separates metrics originating from this app's whitebox instrumentation from
     * those originating from other Wavefront integrations.
     * @return A prefix to add to every metric.
     */
    @Nullable
    default String globalPrefix() {
        return getString(this, "globalPrefix").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> PushRegistryConfig.validate(c), checkRequired("source", WavefrontConfig::source));
    }

    default Validated<?> validateSenderConfiguration() {
        return checkAll(this, c -> validate(), checkRequired("uri", WavefrontConfig::uri),
                check("apiToken", WavefrontConfig::apiToken)
                    .andThen(v -> v.invalidateWhen(token -> token == null && WavefrontMeterRegistry.isDirectToApi(this),
                            "must be set whenever publishing directly to the Wavefront API", InvalidReason.MISSING)));
    }

}
