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
import org.apache.http.HttpRequest;

import java.util.function.Function;

public class DefaultApacheHttpClientObservationConvention implements ApacheHttpClientObservationConvention {

    private final Function<HttpRequest, String> uriMapper;

    private final boolean exportTagsForRoute;

    public DefaultApacheHttpClientObservationConvention(Function<HttpRequest, String> uriMapper,
            boolean exportTagsForRoute) {
        this.uriMapper = uriMapper;
        this.exportTagsForRoute = exportTagsForRoute;
    }

    @Override
    public String getName() {
        return MicrometerHttpRequestExecutor.METER_NAME;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        KeyValues keyValues = KeyValues.of(
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.METHOD
                        .of(context.getCarrier().getRequestLine().getMethod()),
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.URI
                        .of(uriMapper.apply(context.getCarrier())),
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.STATUS.of(getStatusValue(context)));
        if (exportTagsForRoute) {
            keyValues = keyValues.and(HttpContextUtils.generateTagStringsForRoute(context.getApacheHttpContext()));
        }
        return keyValues;
    }

    private String getStatusValue(ApacheHttpClientContext context) {
        return context.getResponse() != null ? Integer.toString(context.getResponse().getStatusLine().getStatusCode())
                : "CLIENT_ERROR";
    }

}
