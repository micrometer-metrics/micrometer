/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpRequest;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * classic <pre>{@code
 *         HttpClientBuilder clientBuilder = HttpClientBuilder.create();
 *         ApacheHttpClientMetricsBinder.builder(registry)
 *             .build()
 *             .instrument(clientBuilder);
 *         CloseableHttpClient httpClient = clientBuilder.build();
 *}</pre>
 *
 * async: <pre>{@code
 *         HttpAsyncClientBuilder asyncClientBuilder = HttpAsyncClientBuilder.create();
 *         ApacheHttpClientMetricsBinder.builder(registry)
 *             .build()
 *             .instrument(asyncClientBuilder);
 *
 *         CloseableHttpAsyncClient httpAsyncClient = asyncClientBuilder.build();
 * }</pre>
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @author Tommy Ludwig
 * @author Lars Uffmann
 * @since 1.12.0
 */
public class ApacheHttpClientMetricsBinder {

    public static final String INTERCEPTOR_NAME = "micrometer";

    static final String DEFAULT_METER_NAME = "httpcomponents.httpclient.request";

    private final String meterName;

    private final MeterRegistry registry;

    private final ObservationRegistry observationRegistry;

    private final Iterable<Tag> extraTags;

    private final Function<HttpRequest, String> uriMapper;

    private final boolean exportTagsForRoute;

    private final ApacheHttpClientObservationConvention observationConvention;

    private final boolean meterRetries;

    ApacheHttpClientMetricsBinder(String meterName, MeterRegistry registry, Function<HttpRequest, String> uriMapper,
            Iterable<Tag> extraTags, boolean exportTagsForRoute, ObservationRegistry observationRegistry,
            ApacheHttpClientObservationConvention observationConvention, boolean meterRetries) {
        this.meterName = meterName;
        this.registry = Objects.requireNonNull(registry);
        this.uriMapper = Objects.requireNonNull(uriMapper);
        this.extraTags = Objects.requireNonNull(extraTags);
        this.exportTagsForRoute = exportTagsForRoute;
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
        this.meterRetries = meterRetries;
    }

    public void instrument(HttpClientBuilder clientBuilder) {
        Objects.requireNonNull(clientBuilder);
        final MeteringExecChainHandler execChainHandler = new MeteringExecChainHandler(registry, meterName, uriMapper,
                extraTags, exportTagsForRoute, observationRegistry, observationConvention);
        if (meterRetries) {
            clientBuilder.addExecInterceptorAfter(ChainElement.RETRY.name(), INTERCEPTOR_NAME, execChainHandler);
        }
        else {
            clientBuilder.addExecInterceptorFirst(INTERCEPTOR_NAME, execChainHandler);
        }
    }

    /**
     * Instrument the clientBuilder and immediately build the client.
     * @param clientBuilder - the clientBuilder to instrument
     * @return the fully configured CloseableHttpClient
     */
    public CloseableHttpClient instrumentAndGet(HttpClientBuilder clientBuilder) {
        instrument(clientBuilder);
        return clientBuilder.build();
    }

    public void instrument(HttpAsyncClientBuilder asyncClientBuilder) {
        Objects.requireNonNull(asyncClientBuilder);
        final MeteringAsyncExecChainHandler execChainHandler = new MeteringAsyncExecChainHandler(registry,
                observationRegistry, observationConvention, meterName, uriMapper, exportTagsForRoute, extraTags,
                meterRetries);
        if (meterRetries) {
            asyncClientBuilder.addExecInterceptorAfter(ChainElement.RETRY.name(), INTERCEPTOR_NAME, execChainHandler);
        }
        else {
            asyncClientBuilder.addExecInterceptorFirst(INTERCEPTOR_NAME, execChainHandler);
        }
    }

    /**
     * Instrument the asyncClientBuilder and immediately build the client.
     * @param asyncClientBuilder - the asyncClientBuilder to instrument
     * @return the fully configured CloseableHttpAsyncClient
     */
    public CloseableHttpAsyncClient instrumentAndGet(HttpAsyncClientBuilder asyncClientBuilder) {
        instrument(asyncClientBuilder);
        return asyncClientBuilder.build();
    }

    /**
     * Use this method to create an instance of {@link ApacheHttpClientMetricsBinder}.
     * @param registry The registry to register the metrics to.
     * @return An instance of the builder, which allows further configuration of the
     * request executor.
     */
    public static ApacheHttpClientMetricsBinder.Builder builder(MeterRegistry registry) {
        return new ApacheHttpClientMetricsBinder.Builder(registry);
    }

    public static class Builder {

        private final MeterRegistry registry;

        private String meterName = DEFAULT_METER_NAME;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private Iterable<Tag> extraTags = Collections.emptyList();

        private Function<HttpRequest, String> uriMapper = new DefaultUriMapper();

        private boolean exportTagsForRoute = false;

        private boolean meterRetries = false;

        @Nullable
        private ApacheHttpClientObservationConvention observationConvention;

        Builder(MeterRegistry registry) {
            this.registry = registry;
        }

        /**
         * Measurements will be exported using the supplier meterName.
         * @param meterName - the meterName
         * @return This builder instance.
         */
        public Builder meterName(String meterName) {
            // Assert.hasText() ...
            this.meterName = Objects.requireNonNull(meterName);
            return this;
        }

        /**
         * These tags will not be applied when instrumentation is performed with the
         * {@link Observation} API. Configure an
         * {@link ApacheHttpClientObservationConvention} instead with the extra key
         * values.
         * @param tags Additional tags which should be exposed with every value.
         * @return This builder instance.
         * @see #observationConvention(ApacheHttpClientObservationConvention)
         * @see #observationRegistry(ObservationRegistry)
         * @see DefaultApacheHttpClientObservationConvention
         */
        public Builder tags(Iterable<Tag> tags) {
            this.extraTags = Optional.ofNullable(tags).orElse(Collections.emptyList());
            return this;
        }

        /**
         * Allows to register a mapping function for exposing request URIs. Be careful,
         * exposing request URIs could result in a huge number of tag values, which could
         * cause problems in your meter registry.
         *
         * By default, this feature is almost disabled. It only exposes values of the
         * {@value DefaultUriMapper#URI_PATTERN_HEADER} HTTP header.
         * @param uriMapper A mapper that allows mapping and exposing request paths.
         * @return This builder instance.
         * @see DefaultUriMapper
         */
        public Builder uriMapper(Function<HttpRequest, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Allows to expose the target scheme, host and port with every metric. Be careful
         * with enabling this feature: If your client accesses a huge number of remote
         * servers, this would result in a huge number of tag values, which could cause
         * cardinality problems.
         *
         * By default, this feature is disabled.
         * @param exportTagsForRoute Set this to true, if the metrics should be tagged
         * with the target route.
         * @return This builder instance.
         */
        public Builder exportTagsForRoute(boolean exportTagsForRoute) {
            this.exportTagsForRoute = exportTagsForRoute;
            return this;
        }

        /**
         * Configure an observation registry to instrument using the {@link Observation}
         * API instead of directly with a {@link Timer}.
         * @param observationRegistry registry with which to instrument
         * @return This builder instance.
         */
        public Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        /**
         * Provide a custom convention to override the default convention used when
         * instrumenting with the {@link Observation} API. This only takes effect when an
         * {@link #observationRegistry(ObservationRegistry)} is configured.
         * @param convention semantic convention to use
         * @return This builder instance.
         * @see #observationRegistry(ObservationRegistry)
         */
        public Builder observationConvention(ApacheHttpClientObservationConvention convention) {
            this.observationConvention = convention;
            return this;
        }

        /**
         * Apache HttpClient has a build in retry facility wqhich is active by default.
         * This parameter decides whether to monitor retries individually or as a single
         * observation.
         * @param meterRetries whether to meter retries
         * @return This builder instance.
         */
        public Builder meterRetries(boolean meterRetries) {
            this.meterRetries = meterRetries;
            return this;
        }

        /**
         * Creates an instance of {@link ApacheHttpClientMetricsBinder} with all the
         * configured properties.
         * @return the fully configured ApacheHttpClientMetricsBinder instance
         */
        public ApacheHttpClientMetricsBinder build() {
            return new ApacheHttpClientMetricsBinder(meterName, registry, uriMapper, extraTags, exportTagsForRoute,
                    observationRegistry, observationConvention, meterRetries);
        }

    }

}
