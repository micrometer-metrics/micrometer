/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import java.net.URI;

import io.micrometer.common.KeyValues;

/**
 * Default {@link HttpJakartaServerRequestObservationConvention}.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.12.0
 */
public class DefaultHttpJakartaClientRequestObservationConvention extends
        AbstractDefaultHttpClientRequestObservationConvention implements HttpJakartaClientRequestObservationConvention {

    private static final String DEFAULT_NAME = "http.client.requests";

    private final String name;

    /**
     * Create a convention with the default name {@code "http.client.requests"}.
     */
    public DefaultHttpJakartaClientRequestObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * Create a convention with a custom name.
     * @param name the observation name
     */
    public DefaultHttpJakartaClientRequestObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(HttpJakartaClientRequestObservationContext context) {
        String method = context.getCarrier() != null
                ? (context.getCarrier().getMethod() != null ? context.getCarrier().getMethod() : null) : null;
        return getContextualName(method);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpJakartaClientRequestObservationContext context) {
        URI uri = context.getCarrier() != null ? context.getCarrier().getUriInfo().getRequestUri() : null;
        Throwable throwable = context.getError();
        String methodName = context.getCarrier() != null ? context.getCarrier().getMethod() : null;
        Integer statusCode = context.getResponse() != null ? context.getResponse().getStatus() : null;
        String uriPathPattern = context.getUriTemplate();
        return getLowCardinalityKeyValues(uri, throwable, methodName, statusCode, uriPathPattern);
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(HttpJakartaClientRequestObservationContext context) {
        URI uri = context.getCarrier() != null ? context.getCarrier().getUriInfo().getRequestUri() : null;
        String userAgent = context.getCarrier() != null ? context.getCarrier().getHeaderString("User-Agent") : null;
        return getHighCardinalityKeyValues(uri, userAgent);
    }

}
