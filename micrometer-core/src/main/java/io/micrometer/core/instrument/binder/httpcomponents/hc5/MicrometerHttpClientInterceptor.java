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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Provides {@link HttpRequestInterceptor} and {@link HttpResponseInterceptor} for
 * instrumenting async Apache HTTP Client 5. Configure the interceptors on an
 * {@link org.apache.hc.client5.http.async.HttpAsyncClient}. Usage example: <pre>{@code
 *     MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry,
 *             HttpRequest::getRequestUri,
 *             Tags.empty(),
 *             true);
 *
 *     CloseableHttpAsyncClient httpAsyncClient = HttpAsyncClients.custom()
 *                 .addRequestInterceptorFirst(interceptor.getRequestInterceptor())
 *                 .addResponseInterceptorLast(interceptor.getResponseInterceptor())
 *                 .build();
 * }</pre>
 *
 * @author Jon Schneider
 * @since 1.11.0
 * @deprecated since 1.12.0 in favor of {@link ObservationExecChainHandler}.
 */
@Deprecated
public class MicrometerHttpClientInterceptor {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(MicrometerHttpClientInterceptor.class);

    private static final String METER_NAME = "httpcomponents.httpclient.request";

    private final Map<HttpContext, Timer.ResourceSample> timerByHttpContext = new ConcurrentHashMap<>();

    private final HttpRequestInterceptor requestInterceptor;

    private final HttpResponseInterceptor responseInterceptor;

    /**
     * Create a {@code MicrometerHttpClientInterceptor} instance.
     * @param meterRegistry meter registry to bind
     * @param uriMapper URI mapper to create {@code uri} tag
     * @param extraTags extra tags
     * @param exportTagsForRoute whether to export tags for route
     */
    public MicrometerHttpClientInterceptor(MeterRegistry meterRegistry, Function<HttpRequest, String> uriMapper,
            Iterable<Tag> extraTags, boolean exportTagsForRoute) {
        log.warn(
                "This class has been deprecated. Please use ObservationExecChainHandler for Apache HTTP client 5 support instead.");

        this.requestInterceptor = (request, entityDetails, context) -> timerByHttpContext.put(context,
                Timer.resource(meterRegistry, METER_NAME)
                    .tags("method", request.getMethod(), "uri", uriMapper.apply(request)));

        this.responseInterceptor = (response, entityDetails, context) -> {
            timerByHttpContext.remove(context)
                .tag("status", Integer.toString(response.getCode()))
                .tag("outcome", Outcome.forStatus(response.getCode()).name())
                .tags(exportTagsForRoute ? HttpContextUtils.generateTagsForRoute(context) : Tags.empty())
                .tags(extraTags)
                .close();
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

    public HttpRequestInterceptor getRequestInterceptor() {
        return requestInterceptor;
    }

    public HttpResponseInterceptor getResponseInterceptor() {
        return responseInterceptor;
    }

}
