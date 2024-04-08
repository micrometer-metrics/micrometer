/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.ObservationExecChainHandler;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

/**
 * This HttpRequestExecutor tracks the request duration of every request, that goes
 * through an {@link org.apache.http.client.HttpClient}. It must be registered as request
 * executor when creating the HttpClient instance. For example:
 *
 * <pre>
 *     HttpClientBuilder.create()
 *         .setRequestExecutor(MicrometerHttpRequestExecutor
 *                 .builder(meterRegistry)
 *                 .build())
 *         .build();
 * </pre>
 * <p>
 * See {@link ObservationExecChainHandler} for Apache HTTP client 5 support.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @author Tommy Ludwig
 * @since 1.2.0
 * @deprecated as of 1.12.0 in favor of HttpComponents 5.x and
 * {@link ObservationExecChainHandler}.
 */
@Incubating(since = "1.2.0")
@Deprecated
public class MicrometerHttpRequestExecutor extends HttpRequestExecutor {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(MicrometerHttpRequestExecutor.class);

    /**
     * Default header name for URI pattern.
     * @deprecated use {@link DefaultUriMapper#URI_PATTERN_HEADER} since 1.4.0
     */
    @Deprecated
    public static final String DEFAULT_URI_PATTERN_HEADER = DefaultUriMapper.URI_PATTERN_HEADER;

    static final String METER_NAME = "httpcomponents.httpclient.request";

    private final MeterRegistry registry;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ApacheHttpClientObservationConvention convention;

    private final Function<HttpRequest, String> uriMapper;

    private final Iterable<Tag> extraTags;

    private final boolean exportTagsForRoute;

    /**
     * Use {@link #builder(MeterRegistry)} to create an instance of this class.
     */
    private MicrometerHttpRequestExecutor(int waitForContinue, MeterRegistry registry,
            Function<HttpRequest, String> uriMapper, Iterable<Tag> extraTags, boolean exportTagsForRoute,
            ObservationRegistry observationRegistry, @Nullable ApacheHttpClientObservationConvention convention) {
        super(waitForContinue);

        log.warn(
                "This class has been deprecated. Please use ObservationExecChainHandler for Apache HTTP client 5 support instead.");

        this.registry = Optional.ofNullable(registry)
            .orElseThrow(() -> new IllegalArgumentException("registry is required but has been initialized with null"));
        this.uriMapper = Optional.ofNullable(uriMapper)
            .orElseThrow(
                    () -> new IllegalArgumentException("uriMapper is required but has been initialized with null"));
        this.extraTags = Optional.ofNullable(extraTags).orElse(Collections.emptyList());
        this.exportTagsForRoute = exportTagsForRoute;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    /**
     * Use this method to create an instance of {@link MicrometerHttpRequestExecutor}.
     * @param registry The registry to register the metrics to.
     * @return An instance of the builder, which allows further configuration of the
     * request executor.
     */
    public static Builder builder(MeterRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context)
            throws IOException, HttpException {
        ObservationOrTimerCompatibleInstrumentation<ApacheHttpClientContext> sample = ObservationOrTimerCompatibleInstrumentation
            .start(registry, observationRegistry,
                    () -> new ApacheHttpClientContext(request, context, uriMapper, exportTagsForRoute), convention,
                    DefaultApacheHttpClientObservationConvention.INSTANCE);
        String statusCodeOrError = "UNKNOWN";
        Outcome statusOutcome = Outcome.UNKNOWN;

        try {
            HttpResponse response = super.execute(request, conn, context);
            sample.setResponse(response);
            statusCodeOrError = DefaultApacheHttpClientObservationConvention.INSTANCE.getStatusValue(response, null);
            statusOutcome = DefaultApacheHttpClientObservationConvention.INSTANCE.getStatusOutcome(response);
            return response;
        }
        catch (IOException | HttpException | RuntimeException e) {
            statusCodeOrError = "IO_ERROR";
            sample.setThrowable(e);
            throw e;
        }
        finally {
            String status = statusCodeOrError;
            String outcome = statusOutcome.name();
            sample.stop(METER_NAME, "Duration of Apache HttpClient request execution",
                    () -> Tags
                        .of("method", DefaultApacheHttpClientObservationConvention.INSTANCE.getMethodString(request),
                                "uri", uriMapper.apply(request), "status", status, "outcome", outcome)
                        .and(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(context) : Tags.empty())
                        .and(extraTags));
        }
    }

    public static class Builder {

        private final MeterRegistry registry;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private int waitForContinue = HttpRequestExecutor.DEFAULT_WAIT_FOR_CONTINUE;

        private Iterable<Tag> extraTags = Collections.emptyList();

        private Function<HttpRequest, String> uriMapper = new DefaultUriMapper();

        private boolean exportTagsForRoute = false;

        @Nullable
        private ApacheHttpClientObservationConvention observationConvention;

        Builder(MeterRegistry registry) {
            this.registry = registry;
        }

        /**
         * @param waitForContinue Overrides the wait for continue time for this request
         * executor. See {@link HttpRequestExecutor} for details.
         * @return This builder instance.
         */
        public Builder waitForContinue(int waitForContinue) {
            this.waitForContinue = waitForContinue;
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
            this.extraTags = tags;
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
         * @since 1.10.0
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
         * @since 1.10.0
         */
        public Builder observationConvention(ApacheHttpClientObservationConvention convention) {
            this.observationConvention = convention;
            return this;
        }

        /**
         * Creates an instance of {@link MicrometerHttpRequestExecutor} with all the
         * configured properties.
         * @return an instance of {@link MicrometerHttpRequestExecutor}
         */
        public MicrometerHttpRequestExecutor build() {
            return new MicrometerHttpRequestExecutor(waitForContinue, registry, uriMapper, extraTags,
                    exportTagsForRoute, observationRegistry, observationConvention);
        }

    }

}
