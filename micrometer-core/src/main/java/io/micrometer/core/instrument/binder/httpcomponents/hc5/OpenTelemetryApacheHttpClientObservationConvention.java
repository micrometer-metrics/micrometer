/*
 * Copyright 2025 VMware, Inc.
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
import io.micrometer.core.instrument.binder.http.HttpMethods;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.HighCardinalityKeyNames;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.LowCardinalityKeyNames;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implementation of {@link ApacheHttpClientObservationConvention} based on the
 * OpenTelemetry Semantic Conventions v1.36.0 for HTTP clients.
 *
 * @since 1.16.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/blob/v1.36.0/docs/http/README.md">OpenTelemetry
 * Semantic Conventions v1.36.0 for HTTP</a>
 */
public class OpenTelemetryApacheHttpClientObservationConvention implements ApacheHttpClientObservationConvention {

    /**
     * Singleton instance of this convention.
     */
    public static final OpenTelemetryApacheHttpClientObservationConvention INSTANCE = new OpenTelemetryApacheHttpClientObservationConvention();

    private static final KeyValue METHOD_OTHER = LowCardinalityKeyNames.METHOD.withValue("_OTHER");

    private static final KeyValue URL_UNKNOWN = HighCardinalityKeyNames.URL.withValue("UNKNOWN");

    private static final KeyValue STATUS_IO_ERROR = LowCardinalityKeyNames.STATUS.withValue("IO_ERROR");

    private static final KeyValue STATUS_CLIENT_ERROR = LowCardinalityKeyNames.STATUS.withValue("CLIENT_ERROR");

    private static final KeyValue EXCEPTION_NONE = LowCardinalityKeyNames.EXCEPTION.withNoneValue();

    private static final KeyValue OUTCOME_UNKNOWN = LowCardinalityKeyNames.OUTCOME.withValue(Outcome.UNKNOWN.name());

    private static final KeyValue SERVER_ADDRESS_UNKNOWN = LowCardinalityKeyNames.SERVER_ADDRESS.withValue("UNKNOWN");

    private static final KeyValue SERVER_PORT_UNKNOWN = LowCardinalityKeyNames.SERVER_PORT.withValue("UNKNOWN");

    // There is no need to instantiate this class multiple times, but it may be extended,
    // hence protected visibility.
    protected OpenTelemetryApacheHttpClientObservationConvention() {
    }

    @Override
    public String getName() {
        return "http.client.request.duration";
    }

    /**
     * HTTP span names SHOULD be {@code {method} {target}} if there is a (low-cardinality)
     * {@code target} available. If there is no (low-cardinality) {@code {target}}
     * available, HTTP span names SHOULD be {@code {method}}.
     * <p>
     * The {@code {method}} MUST be {@code {http.request.method}} if the method represents
     * the original method known to the instrumentation. In other cases (when Customize
     * Toolbar… is set to {@code _OTHER}), {@code {method}} MUST be HTTP.
     * <p>
     * The {@code target} SHOULD be the {@code {url.template}} for HTTP Client spans if
     * enabled and available.
     * @param context context
     * @return contextual name
     * @see <a href=
     * "https://github.com/open-telemetry/semantic-conventions/blob/v1.36.0/docs/http/http-spans.md#name">OpenTelemetry
     * Semantic Convention HTTP Span Name (v1.36.0)</a>
     */
    @Override
    public String getContextualName(ApacheHttpClientContext context) {
        if (context.getCarrier() == null) {
            return "HTTP";
        }
        String maybeMethod = maybeGetKnownMethod(context.getCarrier());
        return maybeMethod == null ? "HTTP" : maybeMethod;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        return KeyValues.of(exception(context), method(context), status(context), outcome(context),
                serverAddress(context), serverPort(context));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ApacheHttpClientContext context) {
        return KeyValues.of(urlFull(context));
    }

    protected @Nullable String maybeGetKnownMethod(HttpRequest request) {
        String httpMethod = request.getMethod();
        if (HttpMethods.isStandard(httpMethod)) {
            return httpMethod;
        }
        return null;
    }

    /**
     * Extract {@code method} key value from context.
     * @param context HTTP client context
     * @return extracted {@code method} key value
     */
    protected KeyValue method(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        String method;
        if (request != null && (method = maybeGetKnownMethod(request)) != null) {
            return LowCardinalityKeyNames.METHOD.withValue(method);
        }
        return METHOD_OTHER;
    }

    /**
     * Extract server address key value from context.
     * @param context HTTP client context
     * @return extracted server address key value
     */
    protected KeyValue serverAddress(ApacheHttpClientContext context) {
        RouteInfo httpRoute = getHttpRoute(context);
        if (httpRoute != null) {
            return LowCardinalityKeyNames.SERVER_ADDRESS.withValue(httpRoute.getTargetHost().getHostName());
        }
        URI uri = getUri(context);
        if (uri != null && uri.getHost() != null) {
            return LowCardinalityKeyNames.SERVER_ADDRESS.withValue(uri.getHost());
        }
        return SERVER_ADDRESS_UNKNOWN;
    }

    /**
     * Extract server port key value from context.
     * @param context HTTP client context
     * @return extracted server port key value
     */
    protected KeyValue serverPort(ApacheHttpClientContext context) {
        RouteInfo httpRoute = getHttpRoute(context);
        if (httpRoute != null) {
            int port = httpRoute.getTargetHost().getPort();
            return LowCardinalityKeyNames.SERVER_PORT.withValue(String.valueOf(port));
        }
        URI uri = getUri(context);
        if (uri != null && uri.getPort() != -1) {
            return LowCardinalityKeyNames.SERVER_PORT.withValue(String.valueOf(uri.getPort()));
        }
        return SERVER_PORT_UNKNOWN;
    }

    /**
     * Extract {@code exception} key value from context.
     * @param context HTTP client context
     * @return extracted {@code exception} key value
     */
    protected KeyValue exception(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        if (error != null) {
            return LowCardinalityKeyNames.EXCEPTION.withValue(error.getClass().getSimpleName());
        }
        return EXCEPTION_NONE;
    }

    /**
     * Extract status key value from context.
     * @param context HTTP client context
     * @return extracted Status key value
     */
    protected KeyValue status(ApacheHttpClientContext context) {
        Throwable error = context.getError();
        if (error instanceof IOException || error instanceof HttpException || error instanceof RuntimeException) {
            return STATUS_IO_ERROR;
        }
        HttpResponse response = context.getResponse();
        if (response == null) {
            // TODO should we return something like 0 or -1 to adhere to the definition of
            // this as an int?
            return STATUS_CLIENT_ERROR;
        }
        return LowCardinalityKeyNames.STATUS.withValue(String.valueOf(response.getCode()));
    }

    /**
     * Extract {@code outcome} key value from context.
     * @param context HTTP client context
     * @return extracted {@code outcome} key value
     */
    protected KeyValue outcome(ApacheHttpClientContext context) {
        HttpResponse response = context.getResponse();
        if (response == null) {
            return OUTCOME_UNKNOWN;
        }
        return LowCardinalityKeyNames.OUTCOME.withValue(Outcome.forStatus(response.getCode()).name());
    }

    protected KeyValue urlFull(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        URI uri;
        if (request != null && (uri = getUri(context)) != null) {
            return HighCardinalityKeyNames.URL.withValue(uri.toString());
        }
        return URL_UNKNOWN;
    }

    private static @Nullable URI getUri(ApacheHttpClientContext context) {
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

    private static @Nullable RouteInfo getHttpRoute(ApacheHttpClientContext context) {
        return context.getHttpClientContext().getHttpRoute();
    }

}
