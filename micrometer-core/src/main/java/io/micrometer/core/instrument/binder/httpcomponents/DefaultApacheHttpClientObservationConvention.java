/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import java.io.IOException;

/**
 * Default implementation of {@link ApacheHttpClientObservationConvention}.
 * <p>
 * See
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.DefaultApacheHttpClientObservationConvention}
 * for Apache HTTP client 5 support.
 *
 * @since 1.10.0
 * @see ApacheHttpClientObservationDocumentation
 * @deprecated as of 1.12.0 in favor of HttpComponents 5.x and
 * {@link io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationConvention}.
 */
@Deprecated
public class DefaultApacheHttpClientObservationConvention implements ApacheHttpClientObservationConvention {

    /**
     * Singleton instance of this convention.
     */
    public static final DefaultApacheHttpClientObservationConvention INSTANCE = new DefaultApacheHttpClientObservationConvention();

    // There is no need to instantiate this class multiple times, but it may be extended,
    // hence protected visibility.
    protected DefaultApacheHttpClientObservationConvention() {
    }

    @Override
    public String getName() {
        return MicrometerHttpRequestExecutor.METER_NAME;
    }

    @Override
    public String getContextualName(ApacheHttpClientContext context) {
        return "HTTP " + getMethodString(context.getCarrier());
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        KeyValues keyValues = KeyValues.of(
                ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.METHOD
                    .withValue(getMethodString(context.getCarrier())),
                ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.URI
                    .withValue(context.getUriMapper().apply(context.getCarrier())),
                ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.STATUS
                    .withValue(getStatusValue(context.getResponse(), context.getError())),
                ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.OUTCOME
                    .withValue(getStatusOutcome(context.getResponse()).name()));
        if (context.shouldExportTagsForRoute()) {
            keyValues = keyValues.and(HttpContextUtils.generateTagStringsForRoute(context.getApacheHttpContext()));
        }
        return keyValues;
    }

    Outcome getStatusOutcome(@Nullable HttpResponse response) {
        return response != null ? Outcome.forStatus(response.getStatusLine().getStatusCode()) : Outcome.UNKNOWN;
    }

    String getStatusValue(@Nullable HttpResponse response, Throwable error) {
        if (error instanceof IOException || error instanceof HttpException || error instanceof RuntimeException) {
            return "IO_ERROR";
        }

        return response != null ? Integer.toString(response.getStatusLine().getStatusCode()) : "CLIENT_ERROR";
    }

    String getMethodString(@Nullable HttpRequest request) {
        return request != null && request.getRequestLine().getMethod() != null ? request.getRequestLine().getMethod()
                : "UNKNOWN";
    }

}
