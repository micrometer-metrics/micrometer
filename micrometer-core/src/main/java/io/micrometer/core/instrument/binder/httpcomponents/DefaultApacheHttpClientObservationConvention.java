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

/**
 * Default implementation of {@link ApacheHttpClientObservationConvention}
 */
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
        // TODO what if method isn't available?
        return "HTTP " + context.getCarrier().getRequestLine().getMethod();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
        KeyValues keyValues = KeyValues.of(
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.METHOD
                        .withValue(context.getCarrier().getRequestLine().getMethod()),
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.URI
                        .withValue(context.getUriMapper().apply(context.getCarrier())),
                ApacheHttpClientDocumentedObservation.ApacheHttpClientTags.STATUS.withValue(getStatusValue(context)));
        if (context.exportTagsForRoute()) {
            keyValues = keyValues.and(HttpContextUtils.generateTagStringsForRoute(context.getApacheHttpContext()));
        }
        return keyValues;
    }

    private String getStatusValue(ApacheHttpClientContext context) {
        return context.getResponse() != null ? Integer.toString(context.getResponse().getStatusLine().getStatusCode())
                : "CLIENT_ERROR";
    }

}
