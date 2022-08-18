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
package io.micrometer.core.instrument.binder.jdk;

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;

import java.net.http.HttpRequest;

/**
 * Default implementation of {@link HttpClientObservationConvention}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class DefaultHttpClientObservationConvention implements HttpClientObservationConvention {

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpClientContext context) {
        if (context.getCarrier() == null) {
            return KeyValues.empty();
        }
        HttpRequest httpRequest = context.getCarrier().build();
        KeyValues keyValues = KeyValues.of(
                HttpClientDocumentedObservation.LowCardinalityKeys.METHOD.withValue(httpRequest.method()),
                HttpClientDocumentedObservation.LowCardinalityKeys.URI.withValue(httpRequest.uri().toString()));
        if (context.getResponse() != null) {
            keyValues = keyValues.and(HttpClientDocumentedObservation.LowCardinalityKeys.STATUS
                    .withValue(String.valueOf(context.getResponse().statusCode())));
        }
        return keyValues;
    }

    @Override
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
