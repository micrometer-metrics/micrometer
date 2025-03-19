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
import io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.HighCardinalityKeys;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import static io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.HighCardinalityKeys.URL_FULL;
import static io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.LowCardinalityKeys.*;

/**
 * OpenTelemetry implementation of {@link ApacheHttpClientObservationConvention}.
 * <p>
 *
 * @see ApacheHttpClientObservationDocumentation
 * @since 1.16.0
 */
public class OpenTelemetryApacheHttpClientObservationConvention implements ApacheHttpClientObservationConvention {

    /**
     * Singleton instance of this convention.
     */
    public static final OpenTelemetryApacheHttpClientObservationConvention INSTANCE = new OpenTelemetryApacheHttpClientObservationConvention();

    // There is no need to instantiate this class multiple times, but it may be extended,
    // hence protected visibility.
    protected OpenTelemetryApacheHttpClientObservationConvention() {
    }

    @Override
    public String getName() {
        return "http.client.request.duration";
    }

    /**
     * HTTP span names SHOULD be {method} {target} if there is a (low-cardinality) target
     * available. If there is no (low-cardinality) {target} available, HTTP span names
     * SHOULD be {method}.
     * <p>
     * The {method} MUST be {http.request.method} if the method represents the original
     * method known to the instrumentation. In other cases (when {http.request.method} is
     * set to _OTHER), {method} MUST be HTTP.
     * @param context context
     * @return contextual name
     */
    @Override
    public String getContextualName(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        String method = "HTTP";
        if (request != null && request.getMethod() != null) {
            method = request.getMethod();
            HttpClientContext clientContext = context.getHttpClientContext();
            String uriTemplate = (String) clientContext.getAttribute(URI_TEMPLATE_ATTRIBUTE);
            if (uriTemplate != null) {
                return method + " " + uriTemplate;
            }
        }
        return method;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        HttpRequest request = context.getCarrier();
        if (request == null || request.getMethod() == null) {
            return KeyValues.empty();
        }
        KeyValue requestMethod = HTTP_REQUEST_METHOD.withValue(request.getMethod());
        KeyValue requestUri = URL_FULL.withValue(request.getRequestUri());
        KeyValues keyValues = KeyValues.of(requestMethod, requestUri);
        if (context.getResponse() != null) {
            KeyValue responseCode = HTTP_RESPONSE_STATUS_CODE
                .withValue(String.valueOf(context.getResponse().getCode()));
            keyValues = keyValues.and(responseCode);
        }
        ProtocolVersion protocolVersion = request.getVersion();
        if (protocolVersion != null) {
            String version = protocolVersion.getMajor() + "." + protocolVersion.getMinor();
            KeyValue protocol = NETWORK_PROTOCOL_VERSION.withValue(version);
            KeyValue networkProtocolName = NETWORK_PROTOCOL_NAME.withValue(protocolVersion.getProtocol());
            keyValues = keyValues.and(protocol, networkProtocolName);
        }
        if (context.getError() != null) {
            KeyValue error = ERROR_TYPE.withValue(context.getError().getClass().getName());
            keyValues = keyValues.and(error);
        }
        try {
            URI uri = request.getUri();
            KeyValue serverAddress = SERVER_ADDRESS.withValue(uri.getHost());
            keyValues = keyValues.and(serverAddress);
            int uriPort = uri.getPort();
            if (uriPort != -1) {
                KeyValue port = SERVER_PORT.withValue(String.valueOf(uriPort));
                keyValues = keyValues.and(port);
            }
            String uriScheme = uri.getScheme();
            if (uriScheme != null) {
                KeyValue scheme = URL_SCHEME.withValue(uriScheme);
                keyValues = keyValues.and(scheme);
            }
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return keyValues;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ApacheHttpClientContext context) {
        KeyValues keyValues = KeyValues.empty();
        HttpRequest request = context.getCarrier();
        HttpResponse response = context.getResponse();
        HttpClientContext clientContext = context.getHttpClientContext();
        if (request != null) {
            if (request instanceof HttpUriRequest) {
                keyValues = fullUrl((HttpUriRequest) request, keyValues);
            }
            keyValues = setRetries(clientContext, keyValues);
            HttpHost target = null;
            RouteInfo route = clientContext.getHttpRoute();
            if (route != null) {
                target = route.getTargetHost();
            }
            if (target != null) {
                String hostName = target.getHostName();
                if (hostName != null) {
                    keyValues = keyValues.and(HighCardinalityKeys.NETWORK_PEER_ADDRESS.withValue(hostName));
                    keyValues = networkPeerPort(target, keyValues);
                }
            }
            String transport = transport(request, target);
            keyValues = keyValues.and(HighCardinalityKeys.NETWORK_TRANSPORT.withValue(transport));
            Header userAgentHeader = request.getFirstHeader("User-Agent");
            if (userAgentHeader != null) {
                keyValues = keyValues
                    .and(HighCardinalityKeys.USER_AGENT_ORIGINAL.withValue(userAgentHeader.getValue()));
            }
            keyValues = processHeaders(request.headerIterator(), keyValues, HighCardinalityKeys.HTTP_REQUEST_HEADER);
        }
        if (response != null) {
            keyValues = processHeaders(response.headerIterator(), keyValues, HighCardinalityKeys.HTTP_RESPONSE_HEADER);
        }
        return keyValues;
    }

    private KeyValues setRetries(HttpClientContext clientContext, KeyValues keyValues) {
        // TODO: To retrieve execCount we would need to have a custom implementation of
        // HttpRequestRetryStrategy that stores in an attribute execCount that we would
        // retrieve here
        // Integer execCount = ...
        // if (execCount != null && execCount > 0) {
        // keyValues = keyValues.and(
        // HighCardinalityKeys.HTTP_REQUEST_RESEND_COUNT.asString(),
        // String.valueOf(execCount));
        // }
        return keyValues;
    }

    private KeyValues fullUrl(HttpUriRequest request, KeyValues keyValues) {
        try {
            URI uri = request.getUri();
            keyValues = keyValues.and(HighCardinalityKeys.URL_FULL.asString(), uri.toString());
            return keyValues;
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private KeyValues networkPeerPort(HttpHost target, KeyValues keyValues) {
        int port = target.getPort();
        if (port <= 0) {
            port = "https".equalsIgnoreCase(target.getSchemeName()) ? 443 : 80;
        }
        keyValues = keyValues.and(HighCardinalityKeys.NETWORK_PEER_PORT.asString(), String.valueOf(port));
        return keyValues;
    }

    private String transport(HttpRequest request, @Nullable HttpHost target) {
        if (request.getScheme() != null && request.getScheme().equals("unix")) {
            return "unix";
        }
        else if (target != null && target.getSchemeName() != null) {
            String scheme = target.getSchemeName();
            if (scheme.equals("unix")) {
                return "unix";
            }
            else if (scheme.equals("h3") || scheme.equals("quic")) {
                return "udp";
            }
        }
        return "tcp";
    }

    private KeyValues processHeaders(Iterator<Header> response, KeyValues keyValues,
            HighCardinalityKeys responseHeader) {
        while (response.hasNext()) {
            Header header = response.next();
            keyValues = keyValues.and(String.format(responseHeader.name(), header.getName()), header.getValue());
        }
        return keyValues;
    }

}
