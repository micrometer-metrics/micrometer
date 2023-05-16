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
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Provides {@link AsyncExecChainHandler} for instrumenting async Apache HTTP Client 5.
 *
 * @author Jon Schneider
 * @author Lars Uffmann
 * @since 1.12.0
 */
class MeteringAsyncExecChainHandler implements AsyncExecChainHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MeteringAsyncExecChainHandler.class);

    private final MeterRegistry meterRegistry;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ApacheHttpClientObservationConvention convention;

    private final String meterName;

    private final Function<HttpRequest, String> uriMapper;

    private final boolean exportTagsForRoute;

    private final Iterable<Tag> extraTags;

    private final boolean meterRetries;

    public MeteringAsyncExecChainHandler(MeterRegistry meterRegistry, ObservationRegistry observationRegistry,
            @Nullable ApacheHttpClientObservationConvention convention, String meterName,
            Function<HttpRequest, String> uriMapper, boolean exportTagsForRoute, Iterable<Tag> extraTags,
            boolean meterRetries) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.observationRegistry = observationRegistry;
        this.convention = convention;
        this.meterName = Objects.requireNonNull(meterName);
        this.uriMapper = Objects.requireNonNull(uriMapper);
        this.exportTagsForRoute = Objects.requireNonNull(exportTagsForRoute);
        this.extraTags = Objects.requireNonNull(extraTags);
        this.meterRetries = meterRetries;
    }

    private void meterExecution(HttpRequest request, AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope,
            AsyncExecChain chain, AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

        final Timer.ResourceSample sample = Timer.resource(meterRegistry, meterName)
            .tags("method", request.getMethod(), "uri", uriMapper.apply(request));

        logger.trace("Start Sample: {} execCount {}", sample, scope.execCount.get());

        chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {
            ObservationOrTimerCompatibleInstrumentation<ApacheHttpClientContext> sample = ObservationOrTimerCompatibleInstrumentation
                .start(meterRegistry, observationRegistry,
                        () -> new ApacheHttpClientContext(request, scope.clientContext, uriMapper, exportTagsForRoute),
                        convention, DefaultApacheHttpClientObservationConvention.INSTANCE);

            @Override
            public AsyncDataConsumer handleResponse(HttpResponse response, EntityDetails entityDetails)
                    throws HttpException, IOException {
                String statusCodeOrError = Integer.toString(response.getCode());
                Outcome statusOutcome = Outcome.forStatus(response.getCode());
                sample.setResponse(response);
                sample.stop(meterName, "Duration of Apache HttpClient request execution", () -> Tags
                    .of("method", DefaultApacheHttpClientObservationConvention.INSTANCE.getMethodString(request), "uri",
                            uriMapper.apply(request), "status", statusCodeOrError, "outcome", statusOutcome.name())
                    .and("exception", DefaultApacheHttpClientObservationConvention.INSTANCE.getExceptionString(null))
                    .and(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(scope.clientContext) : Tags.empty())
                    .and(extraTags));

                logger.trace("handleResponse: {} execCount {}", sample, scope.execCount.get());

                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                logger.trace("completed: {}", sample);
                asyncExecCallback.completed();
            }

            @Override
            public void failed(Exception cause) {
                String statusCodeOrError = "IO_ERROR";
                Outcome statusOutcome = Outcome.UNKNOWN;
                sample.setThrowable(cause);
                sample.stop(meterName, "Duration of Apache HttpClient request execution", () -> Tags
                    .of("method", DefaultApacheHttpClientObservationConvention.INSTANCE.getMethodString(request), "uri",
                            uriMapper.apply(request), "status", statusCodeOrError, "outcome", statusOutcome.name())
                    .and("exception", DefaultApacheHttpClientObservationConvention.INSTANCE.getExceptionString(cause))
                    .and(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(scope.clientContext) : Tags.empty())
                    .and(extraTags));
                logger.trace("failed: {} execCount {}", sample, scope.execCount.get());
                asyncExecCallback.failed(cause);
            }
        });
    }

    @Override
    public void execute(HttpRequest request, AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope,
            AsyncExecChain chain, AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        if (meterRetries) {
            meterExecution(request, entityProducer, scope, chain, asyncExecCallback);
        }
        else {
            if (scope.execCount.get() == 1) {
                meterExecution(request, entityProducer, scope, chain, asyncExecCallback);
            }
            else {
                chain.proceed(request, entityProducer, scope, asyncExecCallback);
            }
        }
    }

}
