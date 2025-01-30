/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.java11.instrument.binder.jdk;

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.http.Outcome;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

/**
 * Default implementation of {@link HttpClientObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class DefaultHttpClientObservationConvention implements HttpClientObservationConvention {

    /**
     * Instance of this {@link DefaultHttpClientObservationConvention}.
     */
    public static final DefaultHttpClientObservationConvention INSTANCE = new DefaultHttpClientObservationConvention();

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpClientContext context) {
        if (context.getCarrier() == null) {
            return KeyValues.empty();
        }
        HttpRequest httpRequest = context.getCarrier().build();
        return KeyValues.of(
                HttpClientObservationDocumentation.LowCardinalityKeys.METHOD.withValue(httpRequest.method()),
                HttpClientObservationDocumentation.LowCardinalityKeys.URI
                    .withValue(getUri(httpRequest, context.getUriMapper())),
                HttpClientObservationDocumentation.LowCardinalityKeys.STATUS
                    .withValue(getStatus(context.getResponse())),
                HttpClientObservationDocumentation.LowCardinalityKeys.OUTCOME
                    .withValue(getOutcome(context.getResponse())));
    }

    String getUri(HttpRequest request, Function<HttpRequest, String> uriMapper) {
        return uriMapper.apply(request);
    }

    String getStatus(@Nullable HttpResponse<?> response) {
        if (response == null) {
            return "UNKNOWN";
        }
        return String.valueOf(response.statusCode());
    }

    String getOutcome(@Nullable HttpResponse<?> response) {
        if (response == null) {
            return Outcome.UNKNOWN.name();
        }
        return Outcome.forStatus(response.statusCode()).name();
    }

    @Override
    @NonNull
    public String getName() {
        return "http.client.requests";
    }

    @Nullable
    @Override
    public String getContextualName(HttpClientContext context) {
        if (context.getCarrier() == null) {
            return null;
        }
        return "HTTP " + context.getCarrier().build().method();
    }

}
