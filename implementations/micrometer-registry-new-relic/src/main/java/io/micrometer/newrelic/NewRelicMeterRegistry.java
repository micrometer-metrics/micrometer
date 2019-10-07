/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.newrelic;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;

/**
 * Publishes metrics to New Relic Insights based on provider selected.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Neil Powell
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("new-relic-metrics-publisher");
    private NewRelicClientProvider clientProvider;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, new NewRelicHttpClientProviderImpl(config), clock);
    }
    
    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clientProvider Provider of the HTTP or Agent-based client that publishes metrics to New Relic
     * @param clock  The clock to use for timings.
     */
    public NewRelicMeterRegistry(NewRelicConfig config, NewRelicClientProvider clientProvider, Clock clock) {
        this(config, clientProvider, new NewRelicNamingConvention(), clock, DEFAULT_THREAD_FACTORY);
    }

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clientProvider Provider of the HTTP or Agent-based client that publishes metrics to New Relic
     * @param namingConvention Naming convention to apply before metric publishing
     * @param clock  The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(NewRelicConfig)} instead.
     */
    @Deprecated  // VisibleForTesting
    NewRelicMeterRegistry(NewRelicConfig config, NewRelicClientProvider clientProvider,  
                NamingConvention namingConvention, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        if (clientProvider == null) {
            throw new MissingRequiredConfigurationException("clientProvider required to report metrics to New Relic");
        }
        if (namingConvention == null) {
            throw new MissingRequiredConfigurationException("namingConvention must be set to report metrics to New Relic");
        }
        if (threadFactory == null) {
            throw new MissingRequiredConfigurationException("threadFactory must be set to report metrics to New Relic");
        }
        
        this.clientProvider = clientProvider;

        config().namingConvention(namingConvention);
        start(threadFactory);
    }
    
    @Override
    protected void publish() {
        clientProvider.publish(this, getMeters());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final NewRelicConfig config;

        private NewRelicClientProvider clientProvider;
        private NamingConvention convention = new NewRelicNamingConvention();
        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        Builder(NewRelicConfig config) {
            this.config = config;
            httpClientProvider();
        }

        public Builder agentClientProvider() {
            return clientProvider(new NewRelicAgentClientProviderImpl(config));
        } 

        public Builder httpClientProvider() {
            return clientProvider(new NewRelicHttpClientProviderImpl(config));
        } 

        public Builder clientProvider(NewRelicClientProvider clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }       

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

        public NewRelicMeterRegistry build() {
            return new NewRelicMeterRegistry(config, clientProvider, convention, clock, threadFactory);
        }
    }
}
