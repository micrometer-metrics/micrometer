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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;

import java.io.IOException;
import java.util.function.Function;

/**
 * Provides {@link AsyncExecChainHandler} for instrumenting async Apache HTTP Client 5.
 * Configure the handler {@link org.apache.hc.client5.http.async.HttpAsyncClient}. Usage
 * example: <pre>{@code
 *     MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry,
 *             HttpRequest::getRequestUri,
 *             Tags.empty(),
 *             true);
 *
 *     CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClients.custom()
 *                 .addExecInterceptorFirst("custom", interceptor.getExecChainHandler())
 *                 .build();
 * }</pre>
 *
 * @author Jon Schneider
 * @author Lars Uffmann
 * @since 1.11.0
 */
public class MicrometerHttpClientInterceptor {

    private static final String METER_NAME = "httpcomponents.httpclient.request";

    private final AsyncExecChainHandler execChainHandler;

    /**
     * Create a {@code MicrometerHttpClientInterceptor} instance.
     * @param meterRegistry meter registry to bind
     * @param uriMapper URI mapper to create {@code uri} tag
     * @param extraTags extra tags
     * @param exportTagsForRoute whether to export tags for route
     */
    public MicrometerHttpClientInterceptor(MeterRegistry meterRegistry, Function<HttpRequest, String> uriMapper,
            Iterable<Tag> extraTags, boolean exportTagsForRoute) {

        this.execChainHandler = (request, entityProducer, scope, chain, asyncExecCallback) -> {

            final Timer.ResourceSample sample = Timer.resource(meterRegistry, METER_NAME)
                .tags("method", request.getMethod(), "uri", uriMapper.apply(request));
            chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {
                @Override
                public AsyncDataConsumer handleResponse(HttpResponse response, EntityDetails entityDetails)
                        throws HttpException, IOException {
                    sample.tag("status", Integer.toString(response.getCode()))
                        .tag("outcome", Outcome.forStatus(response.getCode()).name())
                        .tags(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(scope.clientContext)
                                : Tags.empty())
                        .tags(extraTags)
                        .close();
                    return asyncExecCallback.handleResponse(response, entityDetails);
                }

                @Override
                public void handleInformationResponse(HttpResponse response) throws HttpException, IOException {
                    asyncExecCallback.handleInformationResponse(response);
                }

                @Override
                public void completed() {
                    asyncExecCallback.completed();
                }

                @Override
                public void failed(Exception cause) {
                    sample.tag("status", "IO_ERROR")
                        .tag("outcome", "UNKNOWN")
                        // .tag("exception",
                        // HttpRequestTags.exception(exception).getValue())
                        .tags(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(scope.clientContext)
                                : Tags.empty())
                        .tags(extraTags)
                        .close();
                    asyncExecCallback.failed(cause);
                }
            });
        };
    }

    /**
     * Create a {@code MicrometerHttpClientInterceptor} instance with
     * {@link DefaultUriMapper}.
     * @param meterRegistry meter registry to bind
     * @param extraTags extra tags
     * @param exportTagsForRoute whether to export tags for route
     */
    public MicrometerHttpClientInterceptor(MeterRegistry meterRegistry, Iterable<Tag> extraTags,
            boolean exportTagsForRoute) {
        this(meterRegistry, new DefaultUriMapper(), extraTags, exportTagsForRoute);
    }

    public AsyncExecChainHandler getExecChainHandler() {
        return execChainHandler;
    }

}
