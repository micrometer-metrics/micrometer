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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.DocumentedObservation;
import io.micrometer.observation.transport.http.tags.HttpClientKeyValuesConvention;

import java.util.Objects;

/**
 * A {@link DocumentedObservation} for OkHttp3 metrics.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum OkHttpDocumentedObservation implements DocumentedObservation {

    /**
     * Observation when using {@link ObservationRegistry.ObservationNamingConfiguration#DEFAULT} mode.
     */
    DEFAULT {
        @Override
        public String getName() {
            return "%s";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return OkHttpLegacyLowCardinalityTags.values();
        }
    };

    /**
     * Creates a {@link OkHttpDocumentedObservation} depending on the configuration.
     * @param registry observation registry
     * @param okHttpContext the ok http context
     * @param requestsMetricName name of the observation
     * @param keyValuesProvider key values provider. If {@code null} then
     * {@code convention} param must be not {@code null}
     * @param convention http client key values convention when using the
     * {@link ObservationRegistry.ObservationNamingConfiguration#STANDARDIZED} naming
     * convention. Must be not {@code null} when {@code keyValuesProvider} is {@code null}
     * @return a new {@link OkHttpDocumentedObservation}
     */
    static Observation of(@NonNull ObservationRegistry registry, @NonNull OkHttpContext okHttpContext,
            @NonNull String requestsMetricName,
            @Nullable Observation.KeyValuesProvider<OkHttpContext> keyValuesProvider,
            @Nullable HttpClientKeyValuesConvention convention) {
        ObservationRegistry.ObservationNamingConfiguration configuration = registry.observationConfig()
                .getObservationNamingConfiguration();
        Observation.KeyValuesProvider<?> provider = null;
        if (keyValuesProvider != null) {
            provider = keyValuesProvider;
        }
        else if (registry.isNoop() || configuration == ObservationRegistry.ObservationNamingConfiguration.DEFAULT) {
            provider = new DefaultOkHttpKeyValuesProvider();
        }
        else {
            provider = new StandardizedOkHttpKeyValuesProvider(Objects.requireNonNull(convention));
        }
        return Observation.createNotStarted(requestsMetricName, okHttpContext, registry).keyValuesProvider(provider);
    }

    enum OkHttpLegacyLowCardinalityTags implements KeyName {

        TARGET_SCHEME {
            @Override
            public String getKeyName() {
                return "target.scheme";
            }
        },

        TARGET_HOST {
            @Override
            public String getKeyName() {
                return "target.host";
            }
        },

        TARGET_PORT {
            @Override
            public String getKeyName() {
                return "target.port";
            }
        },

        HOST {
            @Override
            public String getKeyName() {
                return "host";
            }
        },

        METHOD {
            @Override
            public String getKeyName() {
                return "method";
            }
        },

        URI {
            @Override
            public String getKeyName() {
                return "uri";
            }
        },

        STATUS {
            @Override
            public String getKeyName() {
                return "status";
            }
        };

    }

}
