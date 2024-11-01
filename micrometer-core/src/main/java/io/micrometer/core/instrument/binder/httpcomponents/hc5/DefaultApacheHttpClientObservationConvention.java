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
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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

    private static final String CONTEXTUAL_NAME_UNKNOWN = "HTTP UNKNOWN";

    private static final KeyValue METHOD_UNKNOWN = ApacheHttpClientKeyNames.METHOD.withValue("UNKNOWN");

    private static final KeyValue URI_UNKNOWN = ApacheHttpClientKeyNames.URI.withValue("UNKNOWN");

    private static final KeyValue STATUS_IO_ERROR = ApacheHttpClientKeyNames.STATUS.withValue("IO_ERROR");

    private static final KeyValue STATUS_CLIENT_ERROR = ApacheHttpClientKeyNames.STATUS.withValue("CLIENT_ERROR");

    private static final KeyValue EXCEPTION_NONE = ApacheHttpClientKeyNames.EXCEPTION.withValue(KeyValue.NONE_VALUE);

    private static final KeyValue OUTCOME_UNKNOWN = ApacheHttpClientKeyNames.OUTCOME.withValue(Outcome.UNKNOWN.name());

    private static final KeyValue TARGET_HOST_UNKNOWN = ApacheHttpClientKeyNames.TARGET_HOST.withValue("UNKNOWN");

    private static final KeyValue TARGET_PORT_UNKNOWN = ApacheHttpClientKeyNames.TARGET_PORT.withValue("UNKNOWN");

    private static final KeyValue TARGET_SCHEME_UNKNOWN = ApacheHttpClientKeyNames.TARGET_SCHEME.withValue("UNKNOWN");

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
        if (request != null && request.getMethod() != null) {
            return "HTTP " + request.getMethod();
        }
        return CONTEXTUAL_NAME_UNKNOWN;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        return KeyValues.of(exception(context), method(context), outcome(context), status(context), targetHost(context),
                targetPort(context), targetScheme(context), uri(context));
    }

    /**
     * Extract {@code exception} key value from context.
     * @param context HTTP client context
     * @return extracted {@code exception} key value
     * @since 1.12.0
     */
    protected KeyValue exception(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        if (error != null) {
            return ApacheHttpClientKeyNames.EXCEPTION.withValue(error.getClass().getSimpleName());
        }
        return EXCEPTION_NONE;
    }

    /**
     * Extract {@code method} key value from context.
     * @param context HTTP client context
     * @return extracted {@code method} key value
     * @since 1.12.0
     */
    protected KeyValue method(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        if (request == null || request.getMethod() == null) {
            return METHOD_UNKNOWN;
        }
        return ApacheHttpClientKeyNames.METHOD.withValue(request.getMethod());
    }

    /**
     * Extract {@code outcome} key value from context.
     * @param context HTTP client context
     * @return extracted {@code outcome} key value
     * @since 1.12.0
     */
    protected KeyValue outcome(ApacheHttpClientContext context) {
        HttpResponse response = context.getResponse();
        if (response == null) {
            return OUTCOME_UNKNOWN;
        }
        return ApacheHttpClientKeyNames.OUTCOME.withValue(Outcome.forStatus(response.getCode()).name());
    }

    /**
     * Extract {@code status} key value from context.
     * @param context HTTP client context
     * @return extracted {@code status} key value
     * @since 1.12.0
     */
    protected KeyValue status(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        if (error instanceof IOException || error instanceof HttpException || error instanceof RuntimeException) {
            return STATUS_IO_ERROR;
        }
        HttpResponse response = context.getResponse();
        if (response == null) {
            return STATUS_CLIENT_ERROR;
        }
        return ApacheHttpClientKeyNames.STATUS.withValue(String.valueOf(response.getCode()));
    }

    /**
     * Extract {@code target.host} key value from context.
     * @param context HTTP client context
     * @return extracted {@code target.host} key value
     * @since 1.12.0
     */
    protected KeyValue targetHost(ApacheHttpClientContext context) {
        RouteInfo httpRoute = getHttpRoute(context);
        if (httpRoute != null) {
            return ApacheHttpClientKeyNames.TARGET_HOST.withValue(httpRoute.getTargetHost().getHostName());
        }
        URI uri = getUri(context);
        if (uri != null && uri.getHost() != null) {
            return ApacheHttpClientKeyNames.TARGET_HOST.withValue(uri.getHost());
        }
        return TARGET_HOST_UNKNOWN;
    }

    @Nullable
    private static URI getUri(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        if (request != null) {
            try {
                return request.getUri();
            }
            catch (URISyntaxException ignored) {
            }
        }
        return null;
    }

    /**
     * Extract {@code target.port} key value from context.
     * @param context HTTP client context
     * @return extracted {@code target.port} key value
     * @since 1.12.0
     */
    protected KeyValue targetPort(ApacheHttpClientContext context) {
        RouteInfo httpRoute = getHttpRoute(context);
        if (httpRoute != null) {
            int port = httpRoute.getTargetHost().getPort();
            return ApacheHttpClientKeyNames.TARGET_PORT.withValue(String.valueOf(port));
        }
        URI uri = getUri(context);
        if (uri != null && uri.getPort() != -1) {
            return ApacheHttpClientKeyNames.TARGET_PORT.withValue(String.valueOf(uri.getPort()));
        }
        return TARGET_PORT_UNKNOWN;
    }

    /**
     * Extract {@code target.scheme} key value from context.
     * @param context HTTP client context
     * @return extracted {@code target.scheme} key value
     * @since 1.12.0
     */
    protected KeyValue targetScheme(ApacheHttpClientContext context) {
        RouteInfo httpRoute = getHttpRoute(context);
        if (httpRoute != null) {
            return ApacheHttpClientKeyNames.TARGET_SCHEME.withValue(httpRoute.getTargetHost().getSchemeName());
        }
        URI uri = getUri(context);
        if (uri != null && uri.getScheme() != null) {
            return ApacheHttpClientKeyNames.TARGET_SCHEME.withValue(String.valueOf(uri.getScheme()));
        }
        return TARGET_SCHEME_UNKNOWN;
    }

    @Nullable
    private static RouteInfo getHttpRoute(ApacheHttpClientContext context) {
        return context.getHttpClientContext().getHttpRoute();
    }

    /**
     * Extract {@code uri} key value from context.
     * @param context HTTP client context
     * @return extracted {@code uri} key value
     * @since 1.12.0
     */
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
