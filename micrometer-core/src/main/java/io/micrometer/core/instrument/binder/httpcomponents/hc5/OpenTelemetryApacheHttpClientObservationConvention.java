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
 * OpenTelemetry implementation of {@link ApacheHttpClientObservationConvention}.
 * <p>
 *
 * @since 1.16.0
 * @see ApacheHttpClientObservationDocumentation
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
     * HTTP span names SHOULD be {method} {target} if there is a (low-cardinality) target available. If there is no (low-cardinality) {target} available, HTTP span names SHOULD be {method}.
     * <p>
     * The {method} MUST be {http.request.method} if the method represents the original method known to the instrumentation. In other cases (when {http.request.method} is set to _OTHER), {method} MUST be HTTP.
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
        return null;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        return null;
    }
}
