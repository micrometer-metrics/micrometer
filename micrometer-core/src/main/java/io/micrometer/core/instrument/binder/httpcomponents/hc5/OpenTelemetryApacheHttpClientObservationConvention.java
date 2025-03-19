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
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolVersion;

import java.net.URI;
import java.net.URISyntaxException;

import static io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.HighCardinalityKeys.URL_FULL;
import static io.micrometer.core.instrument.binder.httpcomponents.hc5.OpenTelemetryApacheHttpClientObservationDocumentation.LowCardinalityKeys.*;

/**
 * OpenTelemetry implementation of {@link ApacheHttpClientObservationConvention}.
 * <p>
 *
 * @see ApacheHttpClientObservationDocumentation
 * @since 1.16.0
 */
public class OpenTelemetryApacheHttpClientObservationConvention implements
    ApacheHttpClientObservationConvention {

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
     * HTTP span names SHOULD be {method} {target} if there is a (low-cardinality) target available.
     * If there is no (low-cardinality) {target} available, HTTP span names SHOULD be {method}.
     * <p>
     * The {method} MUST be {http.request.method} if the method represents the original method known
     * to the instrumentation. In other cases (when {http.request.method} is set to _OTHER),
     * {method} MUST be HTTP.
     *
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
    public KeyValues getHighCardinalityKeyValues(ApacheHttpClientContext context) {
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
            KeyValue networkProtocolName = NETWORK_PROTOCOL_NAME.withValue(
                protocolVersion.getProtocol());
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
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return keyValues;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        return null;
    }

}
