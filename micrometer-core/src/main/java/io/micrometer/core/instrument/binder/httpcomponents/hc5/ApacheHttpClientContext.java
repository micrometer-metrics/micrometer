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

import io.micrometer.observation.transport.RequestReplySenderContext;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.util.function.Function;

/**
 * {@link io.micrometer.observation.Observation.Context} for use with Apache HTTP Client 5
 * {@link io.micrometer.observation.Observation} instrumentation.
 *
 * @author Brian Clozel
 * @since 1.11.0
 */
public class ApacheHttpClientContext extends RequestReplySenderContext<HttpRequest, HttpResponse> {

    @SuppressWarnings("deprecation")
    private static final DefaultUriMapper DEFAULT_URI_MAPPER = new DefaultUriMapper();

    private final HttpClientContext clientContext;

    private final Function<HttpRequest, String> uriMapper;

    private final boolean exportTagsForRoute;

    /**
     * Create a new {@link io.micrometer.observation.Observation.Context observation
     * context} for the Apache HTTP Client 5 instrumentation.
     * @param request the client request
     * @param apacheHttpContext the HTTP client context
     * @param uriMapper the mapper that detects the URI template
     * @param exportTagsForRoute whether route tags should be contributed
     * @deprecated as of 1.12.0 in favor of
     * {@link #ApacheHttpClientContext(HttpRequest, HttpClientContext)}.
     */
    @Deprecated
    public ApacheHttpClientContext(HttpRequest request, HttpContext apacheHttpContext,
            Function<HttpRequest, String> uriMapper, boolean exportTagsForRoute) {
        super((httpRequest, key, value) -> {
            if (httpRequest != null) {
                httpRequest.addHeader(key, value);
            }
        });
        this.uriMapper = uriMapper;
        this.exportTagsForRoute = exportTagsForRoute;
        setCarrier(request);
        this.clientContext = HttpClientContext.adapt(apacheHttpContext);
    }

    /**
     * Create a new {@link io.micrometer.observation.Observation.Context observation
     * context} for the Apache HTTP Client 5 instrumentation.
     * @param request the client request
     * @param apacheHttpContext the HTTP client context
     * @since 1.12.0
     */
    public ApacheHttpClientContext(HttpRequest request, HttpClientContext apacheHttpContext) {
        this(request, apacheHttpContext, DEFAULT_URI_MAPPER, true);
    }

    /**
     * Return the client context associated with the current HTTP request.
     * @deprecated as of 1.12.0 in favor of {@link #getHttpClientContext()}.
     */
    @Deprecated
    public HttpContext getApacheHttpContext() {
        return this.clientContext;
    }

    /**
     * Return the client context associated with the current HTTP request.
     * @return HTTP client context
     * @since 1.12.0
     */
    public HttpClientContext getHttpClientContext() {
        return this.clientContext;
    }

    /**
     * Return the function that extracts the URI template information from the current
     * request.
     * @return URI mapper
     * @deprecated as of 1.12.0 in favor of an {@link HttpClientContext} attribute.
     * @see ApacheHttpClientObservationConvention#URI_TEMPLATE_ATTRIBUTE
     */
    @Deprecated
    public Function<HttpRequest, String> getUriMapper() {
        return this.uriMapper;
    }

    /**
     * Whether the route information should be contributed as tags with metrics.
     * @return whether the route information should be contributed as tags with metrics
     * @deprecated as of 1.12.0 with no replacement.
     */
    @Deprecated
    public boolean shouldExportTagsForRoute() {
        return this.exportTagsForRoute;
    }

}
