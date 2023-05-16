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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides {@link AsyncExecChainHandler} for instrumenting classic Apache HTTP Client 5.
 *
 * @author Lars Uffmann
 * @since 1.12.0
 */
class MeteringExecChainHandler implements ExecChainHandler {

    private final MeterRegistry registry;

    private final String meterName;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ApacheHttpClientObservationConvention convention;

    private final Function<HttpRequest, String> uriMapper;

    private final Iterable<Tag> extraTags;

    private final boolean exportTagsForRoute;

    MeteringExecChainHandler(MeterRegistry registry, String meterName, Function<HttpRequest, String> uriMapper,
            Iterable<Tag> extraTags, boolean exportTagsForRoute, ObservationRegistry observationRegistry,
            @Nullable ApacheHttpClientObservationConvention convention) {
        this.registry = Optional.ofNullable(registry)
            .orElseThrow(() -> new IllegalArgumentException("registry is required but has been initialized with null"));
        // Assert.hasText...
        this.meterName = Objects.requireNonNull(meterName);
        this.uriMapper = Optional.ofNullable(uriMapper)
            .orElseThrow(
                    () -> new IllegalArgumentException("uriMapper is required but has been initialized with null"));
        this.extraTags = Optional.ofNullable(extraTags).orElse(Collections.emptyList());
        this.exportTagsForRoute = exportTagsForRoute;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
    }

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain)
            throws IOException, HttpException {
        ObservationOrTimerCompatibleInstrumentation<ApacheHttpClientContext> sample = ObservationOrTimerCompatibleInstrumentation
            .start(registry, observationRegistry,
                    () -> new ApacheHttpClientContext(request, scope.clientContext, uriMapper, exportTagsForRoute),
                    convention, DefaultApacheHttpClientObservationConvention.INSTANCE);
        String statusCodeOrError = "UNKNOWN";
        Outcome statusOutcome = Outcome.UNKNOWN;
        String exceptionName = "None";

        try {
            ClassicHttpResponse response = chain.proceed(request, scope);
            sample.setResponse(response);
            statusCodeOrError = DefaultApacheHttpClientObservationConvention.INSTANCE.getStatusValue(response, null);
            statusOutcome = DefaultApacheHttpClientObservationConvention.INSTANCE.getStatusOutcome(response);
            exceptionName = DefaultApacheHttpClientObservationConvention.INSTANCE.getExceptionString(null);
            return response;
        }
        catch (IOException | HttpException | RuntimeException e) {
            statusCodeOrError = "IO_ERROR";
            exceptionName = DefaultApacheHttpClientObservationConvention.INSTANCE.getExceptionString(e);
            sample.setThrowable(e);
            throw e;
        }
        finally {
            String status = statusCodeOrError;
            String outcome = statusOutcome.name();
            String exception = exceptionName;
            sample.stop(meterName, "Duration of Apache HttpClient request execution", () -> Tags
                .of("method", DefaultApacheHttpClientObservationConvention.INSTANCE.getMethodString(request), "uri",
                        uriMapper.apply(request), "status", status, "outcome", outcome)
                .and("exception", exception)
                .and(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(scope.clientContext) : Tags.empty())
                .and(extraTags));
        }
    }

}
