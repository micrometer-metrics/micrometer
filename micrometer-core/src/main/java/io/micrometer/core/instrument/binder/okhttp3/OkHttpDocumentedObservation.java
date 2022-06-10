package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.docs.SemanticName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.docs.DocumentedObservation;
import io.micrometer.observation.transport.http.tags.HttpClientKeyValuesConvention;
import io.micrometer.observation.transport.http.tags.OpenTelemetryHttpClientLowCardinalityKeyNames;
import io.micrometer.observation.transport.http.tags.OpenTelemetryHttpClientSemanticName;
import io.micrometer.observation.transport.http.tags.OpenTelemetryHttpLowCardinalityKeyNames;

/**
 * A {@link DocumentedObservation} for OkHttp3 metrics.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum OkHttpDocumentedObservation implements DocumentedObservation {

    LEGACY {
        @Override
        public SemanticName getName() {
            return OkHttpSemanticName.LEGACY;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return OkHttpLegacyLowCardinalityTags.values();
        }
    },

    STANDARD {
        @Override
        public SemanticName getName() {
            return OkHttpSemanticName.STANDARD;
        }

        // TODO: This should not be hardcoded to OTel, what is someone changes the standard?
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(OpenTelemetryHttpLowCardinalityKeyNames.values(), OpenTelemetryHttpClientLowCardinalityKeyNames.values());
        }

    };

    /**
     * Creates a {@link OkHttpDocumentedObservation} depending on the configuration.
     *
     * @param registry observation registry
     * @param keyValuesProvider key values provider
     * @return a new {@link OkHttpDocumentedObservation}
     */
    static Observation of(ObservationRegistry registry, OkHttpContext okHttpContext, @Nullable Observation.KeyValuesProvider<OkHttpContext> keyValuesProvider) {
        ObservationRegistry.ObservationNamingConfiguration configuration = registry.observationConfig().getObservationNamingConfiguration();
        OkHttpDocumentedObservation observation = configuration != ObservationRegistry.ObservationNamingConfiguration.STANDARDIZED ? LEGACY : STANDARD;
        Observation.KeyValuesProvider<?> provider = null;
        if (keyValuesProvider != null) {
            provider = keyValuesProvider;
        }
        else if (registry.isNoop() || registry.observationConfig()
                .getObservationNamingConfiguration() == ObservationRegistry.ObservationNamingConfiguration.LEGACY) {
            provider = new DefaultOkHttpKeyValuesProvider();
        }
        else if (registry.observationConfig()
                .getObservationNamingConfiguration() == ObservationRegistry.ObservationNamingConfiguration.STANDARDIZED) {
            // TODO: Isn't this too much - maybe we should just require the user to
            // set this manually?
            provider = new StandardizedOkHttpKeyValuesProvider(registry.observationConfig()
                    .getKeyValuesConvention(HttpClientKeyValuesConvention.class));
        }
        else {
            provider = new Observation.KeyValuesProvider.CompositeKeyValuesProvider(
                    new DefaultOkHttpKeyValuesProvider(),
                    new StandardizedOkHttpKeyValuesProvider(registry.observationConfig()
                            .getKeyValuesConvention(HttpClientKeyValuesConvention.class)));
        }
        return observation.observation(registry).keyValuesProvider(provider);
    }

    enum OkHttpSemanticName implements SemanticName {

        LEGACY {
            @Override
            public String getName() {
                return "name.was.always.dynamic";
            }
        },

        STANDARD {

            // TODO: This should not be fixed like this, what if someone changes the standard?
            @Override
            public String getName() {
                return OpenTelemetryHttpClientSemanticName.DEFAULT.getName();
            }
        }
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






