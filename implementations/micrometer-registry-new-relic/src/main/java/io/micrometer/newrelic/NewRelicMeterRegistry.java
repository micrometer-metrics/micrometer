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
package io.micrometer.newrelic;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;

/**
 * Publishes metrics to New Relic Insights based on client provider selected (API or Java Agent).
 * Defaults to the REST API client provider.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Neil Powell
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("new-relic-metrics-publisher");

    // VisibleForTesting
    final NewRelicClientProvider clientProvider;

    private final Config thisConfig;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, null, clock);
    }

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clientProvider Provider of the API or Agent-based client that publishes metrics to New Relic
     * @param clock  The clock to use for timings.
     * @since 1.4.0
     */
    public NewRelicMeterRegistry(NewRelicConfig config, @Nullable NewRelicClientProvider clientProvider, Clock clock) {
        this(config, clientProvider, new NewRelicNamingConvention(), clock, DEFAULT_THREAD_FACTORY);
    }

    // VisibleForTesting
    NewRelicMeterRegistry(NewRelicConfig config, @Nullable NewRelicClientProvider clientProvider,
                NamingConvention namingConvention, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        if (clientProvider == null) {
            //default to Insight API client provider if not specified in config or provided
            clientProvider = (config.clientProviderType() == ClientProviderType.INSIGHTS_AGENT)
                    ? new NewRelicInsightsAgentClientProvider(config)
                    : new NewRelicInsightsApiClientProvider(config);
        }

        this.clientProvider = clientProvider;

        thisConfig = new Config() {
            @Override
            public Config namingConvention(NamingConvention convention) {
                NewRelicMeterRegistry.this.clientProvider.setNamingConvention(convention);
                return super.namingConvention(convention);
            }
        };

        config().namingConvention(namingConvention);
        start(threadFactory);
    }

    @Override
    public Config config() {
        return thisConfig;
    }

    public static Builder builder(NewRelicConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        clientProvider.publish(this);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    public static class Builder {
        private final NewRelicConfig config;

        @Nullable
        private NewRelicClientProvider clientProvider;

        private NamingConvention convention = new NewRelicNamingConvention();
        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        @Nullable
        private HttpSender httpClient;

        Builder(NewRelicConfig config) {
            this.config = config;
        }

        /**
         * Use the client provider. This will override {@link NewRelicConfig#clientProviderType()}.
         * @param clientProvider client provider to use
         * @return builder
         * @since 1.4.0
         */
        public Builder clientProvider(NewRelicClientProvider clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        /**
         * Use the naming convention. Defaults to {@link NewRelicNamingConvention}.
         * @param convention naming convention to use
         * @return builder
         * @since 1.4.0
         */
        public Builder namingConvention(NamingConvention convention) {
            this.convention = convention;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        /**
         * @param httpClient http client to use for publishing
         * @return builder
         * @deprecated since 1.4.0 use {@link #clientProvider(NewRelicClientProvider)} instead.
         */
        @Deprecated
        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public NewRelicMeterRegistry build() {
            if (httpClient != null) {
                if (clientProvider != null) {
                    throw new IllegalStateException("Please remove httpClient() configuration as it has been deprecated in favour of clientProvider().");
                }
                clientProvider = new NewRelicInsightsApiClientProvider(config, httpClient, convention);
            }
            return new NewRelicMeterRegistry(config, clientProvider, convention, clock, threadFactory);
        }
    }
}
