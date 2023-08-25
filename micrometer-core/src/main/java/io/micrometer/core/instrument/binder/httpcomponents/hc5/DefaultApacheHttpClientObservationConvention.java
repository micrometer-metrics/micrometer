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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

import java.io.IOException;

/**
 * Default implementation of {@link ApacheHttpClientObservationConvention}.
 *
 * @since 1.11.0
 * @see ApacheHttpClientObservationDocumentation
 */
public class DefaultApacheHttpClientObservationConvention implements ApacheHttpClientObservationConvention {

    /**
     * Singleton instance of this convention.
     */
    public static final DefaultApacheHttpClientObservationConvention INSTANCE = new DefaultApacheHttpClientObservationConvention();

    private static final KeyValue METHOD_UNKNOWN = KeyValue.of(ApacheHttpClientKeyNames.METHOD, "UNKNOWN");

    private static final KeyValue URI_UNKNOWN = KeyValue.of(ApacheHttpClientKeyNames.URI, "UNKNOWN");

    private static final KeyValue STATUS_IO_ERROR = KeyValue.of(ApacheHttpClientKeyNames.STATUS, "IO_ERROR");

    private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(ApacheHttpClientKeyNames.STATUS, "CLIENT_ERROR");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(ApacheHttpClientKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    // There is no need to instantiate this class multiple times, but it may be extended,
    // hence protected visibility.
    protected DefaultApacheHttpClientObservationConvention() {
    }

    @Override
    public String getName() {
        return "httpcomponents.httpclient.request";
    }

    @Override
    public String getContextualName(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        String methodName = "UNKNOWN";
        if (request != null && request.getMethod() != null) {
            methodName = request.getMethod();
        }
        return "HTTP " + methodName;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        return KeyValues.of(exception(context), method(context), outcome(context), status(context), targetHost(context),
                targetPort(context), targetScheme(context), uri(context));
    }

    protected KeyValue exception(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        if (error != null) {
            return KeyValue.of(ApacheHttpClientKeyNames.EXCEPTION, error.getClass().getSimpleName());
        }
        return EXCEPTION_NONE;
    }

    protected KeyValue method(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        if (request == null || request.getMethod() == null) {
            return METHOD_UNKNOWN;
        }
        return ApacheHttpClientKeyNames.METHOD.withValue(request.getMethod());
    }

    protected KeyValue outcome(ApacheHttpClientContext context) {
        HttpResponse response = context.getResponse();
        if (response == null) {
            return KeyValue.of(ApacheHttpClientKeyNames.OUTCOME, Outcome.UNKNOWN.name());
        }
        return KeyValue.of(ApacheHttpClientKeyNames.OUTCOME, Outcome.forStatus(response.getCode()).name());
    }

    protected KeyValue status(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        HttpResponse response = context.getResponse();
        if (error instanceof IOException || error instanceof HttpException || error instanceof RuntimeException) {
            return STATUS_IO_ERROR;
        }
        else if (response == null) {
            return STATUS_CLIENT_ERROR;
        }
        return KeyValue.of(ApacheHttpClientKeyNames.STATUS, Integer.toString(response.getCode()));
    }

    protected KeyValue targetHost(ApacheHttpClientContext context) {
        RouteInfo httpRoute = context.getHttpClientContext().getHttpRoute();
        if (httpRoute != null) {
            return KeyValue.of(ApacheHttpClientKeyNames.TARGET_HOST, httpRoute.getTargetHost().getHostName());
        }
        return KeyValue.of(ApacheHttpClientKeyNames.TARGET_HOST, "UNKNOWN");
    }

    protected KeyValue targetPort(ApacheHttpClientContext context) {
        Object routeAttribute = context.getHttpClientContext().getAttribute("http.route");
        if (routeAttribute instanceof HttpRoute) {
            int port = ((HttpRoute) routeAttribute).getTargetHost().getPort();
            return KeyValue.of(ApacheHttpClientKeyNames.TARGET_PORT, String.valueOf(port));
        }
        return KeyValue.of(ApacheHttpClientKeyNames.TARGET_PORT, "UNKNOWN");
    }

    protected KeyValue targetScheme(ApacheHttpClientContext context) {
        Object routeAttribute = context.getHttpClientContext().getAttribute("http.route");
        if (routeAttribute instanceof HttpRoute) {
            return KeyValue.of(ApacheHttpClientKeyNames.TARGET_SCHEME,
                    ((HttpRoute) routeAttribute).getTargetHost().getSchemeName());
        }
        return KeyValue.of(ApacheHttpClientKeyNames.TARGET_SCHEME, "UNKNOWN");
    }

    @SuppressWarnings("deprecation")
    protected KeyValue uri(ApacheHttpClientContext context) {
        HttpClientContext clientContext = context.getHttpClientContext();
        String uriTemplate = (String) clientContext.getAttribute(URI_TEMPLATE_ATTRIBUTE);
        if (uriTemplate != null) {
            return ApacheHttpClientKeyNames.URI.withValue(uriTemplate);
        }
        if (context.getCarrier() != null) {
            return ApacheHttpClientKeyNames.URI.withValue(context.getUriMapper().apply(context.getCarrier()));
        }
        return URI_UNKNOWN;
    }

    @Deprecated
    Outcome getStatusOutcome(@Nullable HttpResponse response) {
        return response != null ? Outcome.forStatus(response.getCode()) : Outcome.UNKNOWN;
    }

    @Deprecated
    String getStatusValue(@Nullable HttpResponse response, Throwable error) {
        if (error instanceof IOException || error instanceof HttpException || error instanceof RuntimeException) {
            return "IO_ERROR";
        }

        return response != null ? Integer.toString(response.getCode()) : "CLIENT_ERROR";
    }

    @Deprecated
    String getMethodString(@Nullable HttpRequest request) {
        return request != null && request.getMethod() != null ? request.getMethod() : "UNKNOWN";
    }

}
